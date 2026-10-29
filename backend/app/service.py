"""Turning a person's interactions or request into a ranked, explained list of real titles.

Where the scores come from:
  * offline model - the hybrid validated in reports/ (movies in MovieLens only), folded in
    from the person's likes without retraining;
  * TMDB recommendations of each liked title (covers TV and recent releases MovieLens lacks);
  * content similarity between candidate text (genres + overview) and the person's taste.

The serving blend weights below are hand-set, not learned: TMDB candidates have no
offline ground truth to tune against. Only the movie hybrid is validated offline.
"""

from __future__ import annotations

import asyncio
import pickle
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

from recsys.models import mmr

from . import genres
from .schemas import Interaction, QueryIntent, RecItem, TitleRef, TitleSummary
from .tmdb import TMDB

SIGNAL_RATING = {"like": 5.0, "dislike": 1.0, "seen": 3.5}
WEIGHTS = {"content": 0.35, "votes": 0.30, "offline": 0.25, "quality": 0.10}
SEEDS = 8


@dataclass
class Candidate:
    title: TitleSummary
    seed_votes: dict[tuple[str, int], float] = field(default_factory=dict)
    offline: float = 0.0
    facets: list[str] = field(default_factory=list)


class OfflineModel:
    """The trained MovieLens hybrid, addressed by TMDB movie id."""

    def __init__(self, path: Path):
        with path.open("rb") as f:
            bundle = pickle.load(f)  # our own artifact, built by recsys.train in the Docker image
        self.model = bundle["model"]
        self.tmdb_to_item: dict[int, int] = bundle["tmdb_to_item"]
        self.item_to_tmdb: dict[int, int] = bundle["item_to_tmdb"]

    def recommend(self, interactions: list[Interaction], k: int) -> list[tuple[int, float]]:
        pairs = [(self.tmdb_to_item[i.id], SIGNAL_RATING[i.signal]) for i in interactions
                 if i.media_type == "movie" and i.signal in SIGNAL_RATING and i.id in self.tmdb_to_item]
        if not any(r >= 4 for _, r in pairs):
            return []
        items = np.array([p[0] for p in pairs], dtype=np.int64)
        ratings = np.array([p[1] for p in pairs], dtype=np.float32)
        top = self.model.recommend(items, ratings, k)
        return [(self.item_to_tmdb[i], 1.0 - rank / k) for rank, i in enumerate(top) if i in self.item_to_tmdb]


def quality(t: TitleSummary) -> float:
    """Bayesian-averaged rating in [0, 1] so a 9.0 from 12 votes does not beat an 8.4 from 20,000."""
    prior, weight = 6.5, 200
    return (t.vote_average * t.vote_count + prior * weight) / (t.vote_count + weight) / 10.0


def text_of(t: TitleSummary) -> str:
    g = " ".join(x.replace(" ", "") for x in t.genres)
    return f"{g} {g} {t.overview}"


def embed(texts: list[str]) -> np.ndarray:
    try:
        matrix = TfidfVectorizer(stop_words="english", sublinear_tf=True).fit_transform(texts)
    except ValueError:  # every document empty after stop words
        return np.zeros((len(texts), 1), np.float32)
    return normalize(matrix).toarray().astype(np.float32)


class Recommender:
    def __init__(self, tmdb: TMDB, offline: OfflineModel | None):
        self.tmdb = tmdb
        self.offline = offline

    async def recommend(
        self, interactions: list[Interaction], media_type: str, limit: int
    ) -> tuple[list[RecItem], str]:
        latest: dict[tuple[str, int], Interaction] = {}
        for i in interactions:  # a later signal on the same title wins
            latest[(i.media_type, i.id)] = i
        interactions = list(latest.values())
        likes = [i for i in interactions if i.signal == "like"]
        dislikes = [i for i in interactions if i.signal == "dislike"]
        excluded = set(latest)
        if not likes:
            return await self._cold_start(excluded, media_type, limit), "cold_start"

        seeds = likes[-SEEDS:]
        seed_titles = await self._summaries([(s.media_type, s.id) for s in seeds + dislikes[-SEEDS:]])
        candidates: dict[tuple[str, int], Candidate] = {}

        rec_lists = await asyncio.gather(*(self.tmdb.recommendations(s.media_type, s.id) for s in seeds))
        for seed, recs in zip(seeds, rec_lists, strict=True):
            for pos, t in enumerate(recs):
                c = candidates.setdefault(t.key(), Candidate(t))
                c.seed_votes[(seed.media_type, seed.id)] = 1.0 - pos / max(len(recs), 1)

        if self.offline:
            offline = self.offline.recommend(interactions, 30)
            known = {k[1]: c for k, c in candidates.items() if k[0] == "movie"}
            missing = [tid for tid, _ in offline if tid not in known]
            for t in await self._summaries([("movie", tid) for tid in missing]):
                known[t.id] = candidates.setdefault(t.key(), Candidate(t))
            for tid, s in offline:
                if tid in known:
                    known[tid].offline = s

        pool = [c for k, c in candidates.items()
                if k not in excluded and (media_type == "all" or k[0] == media_type)]
        liked = [t for t in seed_titles if (t.media_type, t.id) in {(s.media_type, s.id) for s in seeds}]
        disliked = [t for t in seed_titles if t not in liked]
        return self._rank(pool, liked, disliked, limit), "personalized"

    def _rank(self, pool: list[Candidate], liked: list[TitleSummary], disliked: list[TitleSummary],
              limit: int, query_text: str | None = None, query_weight: float = 0.0) -> list[RecItem]:
        if not pool:
            return []
        texts = [text_of(c.title) for c in pool] + [text_of(t) for t in liked + disliked]
        if query_text:
            texts.append(query_text)
        vecs = embed(texts)
        cand, rest = vecs[: len(pool)], vecs[len(pool):]
        liked_v, disliked_v = rest[: len(liked)], rest[len(liked): len(liked) + len(disliked)]

        profile = liked_v.sum(axis=0) - 0.5 * disliked_v.sum(axis=0) if len(liked_v) else np.zeros(cand.shape[1])
        norm = np.linalg.norm(profile)
        taste = cand @ (profile / norm) if norm > 0 else np.zeros(len(pool))
        n_seeds = max(len(liked), 1)
        votes = np.array([sum(c.seed_votes.values()) / n_seeds for c in pool])
        offline = np.array([c.offline for c in pool])
        qual = np.array([quality(c.title) for c in pool])
        score = (WEIGHTS["content"] * taste + WEIGHTS["votes"] * votes
                 + WEIGHTS["offline"] * offline + WEIGHTS["quality"] * qual)
        if query_text:
            score = (1 - query_weight) * score + query_weight * (cand @ rest[-1])

        order = np.argsort(-score)[: max(limit * 3, 30)].tolist()
        picked = mmr(order, score[order], cand, limit, lam=0.75)
        return [self._explain(pool[i], score[i], cand[i], liked, liked_v) for i in picked]

    @staticmethod
    def _explain(c: Candidate, score: float, vec: np.ndarray, liked: list[TitleSummary],
                 liked_v: np.ndarray) -> RecItem:
        """Every reason points at a real title the person rated, or at the request facets it matched."""
        sims = liked_v @ vec if len(liked) else np.array([])
        by_key = {(t.media_type, t.id): i for i, t in enumerate(liked)}
        voters = [by_key[k] for k in c.seed_votes if k in by_key]
        because: TitleSummary | None = None
        if voters:
            because = liked[max(voters, key=lambda i: sims[i])]
            reason = f"Fans of {because.title} also loved this"
        elif len(sims):
            because = liked[int(np.argmax(sims))]
            shared = [g for g in c.title.genres if g in because.genres][:2]
            reason = (f"Because you liked {because.title}" + (f" · {' & '.join(shared)}" if shared else ""))
        else:
            reason = " · ".join(c.facets) if c.facets else "Highly rated and trending"
        if c.facets and because is not None:
            reason = " · ".join(c.facets[:2]) + f" · like {because.title}"
        ref = TitleRef(id=because.id, media_type=because.media_type, title=because.title) if because else None
        return RecItem(title=c.title, score=round(float(score), 4), reason=reason, because=ref)

    async def _cold_start(self, excluded: set, media_type: str, limit: int) -> list[RecItem]:
        kinds = ["movie", "tv"] if media_type == "all" else [media_type]
        lists = await asyncio.gather(*(self.tmdb.trending(k) for k in kinds), *(self.tmdb.top_rated(k) for k in kinds))
        seen: dict[tuple[str, int], Candidate] = {}
        for idx, titles in enumerate(lists):
            label = "Trending this week" if idx < len(kinds) else "All-time favourite"
            for t in titles:
                if t.key() not in excluded:
                    seen.setdefault(t.key(), Candidate(t, facets=[label]))
        return self._rank(list(seen.values()), [], [], limit)

    async def _summaries(self, keys: list[tuple[str, int]]) -> list[TitleSummary]:
        found = await asyncio.gather(*(self.tmdb.summary(mt, tid) for mt, tid in keys))
        return [t for t in found if t is not None]

    async def onboarding(self) -> list[TitleSummary]:
        """Recognisable titles spread across genres, so a few ratings reveal a lot about taste."""
        lists = await asyncio.gather(self.tmdb.trending("movie"), self.tmdb.trending("tv"),
                                     self.tmdb.top_rated("movie"), self.tmdb.top_rated("tv"))
        pool: dict[tuple[str, int], TitleSummary] = {}
        for titles in lists:
            for t in titles:
                if t.poster_path and t.vote_count >= 300:
                    pool.setdefault(t.key(), t)
        titles = list(pool.values())
        if not titles:
            return []
        vocab = sorted({g for t in titles for g in t.genres})
        vecs = normalize(np.array([[1.0 if g in t.genres else 0.0 for g in vocab] for t in titles]) + 1e-6)
        rel = np.array([np.log1p(t.vote_count) for t in titles])
        picked = mmr(list(range(len(titles))), rel, vecs, 24, lam=0.5)
        return [titles[i] for i in picked]

    async def ask(self, intent: QueryIntent, query: str, interactions: list[Interaction],
                  limit: int) -> tuple[list[RecItem], list[TitleRef]]:
        kinds = ["movie", "tv"] if intent.media_type == "any" else [intent.media_type]

        refs: list[TitleSummary] = []
        for name in intent.reference_titles:
            hits = [h for h in await self.tmdb.search(name) if h.media_type in ("movie", "tv")]
            if hits:
                refs.append(hits[0])
        keyword_ids = [k for k in await asyncio.gather(*(self.tmdb.keyword_id(w) for w in intent.keywords)) if k]

        candidates: dict[tuple[str, int], Candidate] = {}
        ref_lists = await asyncio.gather(*(self.tmdb.recommendations(r.media_type, r.id) for r in refs))
        for ref, recs in zip(refs, ref_lists, strict=True):
            for pos, t in enumerate(recs):
                c = candidates.setdefault(t.key(), Candidate(t))
                c.seed_votes[(ref.media_type, ref.id)] = 1.0 - pos / max(len(recs), 1)

        discover_lists = await asyncio.gather(*(self._discover(k, intent, keyword_ids) for k in kinds))
        for titles in discover_lists:
            for t in titles:
                candidates.setdefault(t.key(), Candidate(t))

        excluded = {(i.media_type, i.id) for i in interactions if i.signal in ("seen", "dislike", "dismiss")}
        excluded |= {r.key() for r in refs}
        pool = [c for k, c in candidates.items()
                if k not in excluded and t_matches(c.title, intent) and k[0] in kinds]
        for c in pool:
            c.facets = facets(c.title, intent)

        liked_keys = [(i.media_type, i.id) for i in interactions if i.signal == "like"][-SEEDS:]
        liked = refs + await self._summaries(liked_keys)
        query_text = " ".join([query, *intent.keywords, *intent.genres] + [text_of(r) for r in refs])
        items = self._rank(pool, liked, [], limit, query_text=query_text, query_weight=0.5)
        ref_out = [TitleRef(id=r.id, media_type=r.media_type, title=r.title) for r in refs]
        return items, ref_out

    async def _discover(self, media_type: str, intent: QueryIntent, keyword_ids: list[int]) -> list[TitleSummary]:
        date_field = "primary_release_date" if media_type == "movie" else "first_air_date"
        params = {
            "sort_by": "popularity.desc",
            "vote_count.gte": 80,
            "with_genres": ",".join(map(str, genres.ids_for(intent.genres, media_type))) or None,
            "without_genres": ",".join(map(str, genres.ids_for(intent.exclude_genres, media_type))) or None,
            "with_keywords": "|".join(map(str, keyword_ids)) or None,
            "vote_average.gte": intent.min_rating,
            "with_original_language": intent.language,
            f"{date_field}.gte": f"{intent.year_min}-01-01" if intent.year_min else None,
            f"{date_field}.lte": f"{intent.year_max}-12-31" if intent.year_max else None,
        }
        if intent.genres and not genres.ids_for(intent.genres, media_type):
            return []  # e.g. "horror" has no TV genre id; avoid returning unfiltered TV
        results = await self.tmdb.discover(media_type, **params)
        if len(results) < 10 and params["with_keywords"]:
            results += await self.tmdb.discover(media_type, **{**params, "with_keywords": None})
        if len(results) < 10 and "," in (params["with_genres"] or ""):
            any_genre = params["with_genres"].replace(",", "|")
            results += await self.tmdb.discover(media_type, **{**params, "with_genres": any_genre})
        return results


def t_matches(t: TitleSummary, intent: QueryIntent) -> bool:
    """Hard constraints are enforced on every candidate, including ones from 'like X' expansion."""
    if intent.year_min and (t.year is None or t.year < intent.year_min):
        return False
    if intent.year_max and (t.year is None or t.year > intent.year_max):
        return False
    banned = {g for g in _tmdb_genre_names(intent.exclude_genres, t.media_type)}
    return not banned & set(t.genres)


def _tmdb_genre_names(canonical: list[str], media_type: str) -> list[str]:
    table = genres.MOVIE if media_type == "movie" else genres.TV
    ids = genres.ids_for(canonical, media_type)
    # "Sci-Fi & Fantasy" on TV covers two canonical genres; only ban it when both are excluded
    return [table[i] for i in ids if not (i == 10765 and not {"science fiction", "fantasy"} <= set(canonical))
            and not (i == 10759 and not {"action", "adventure"} <= set(canonical))]


def facets(t: TitleSummary, intent: QueryIntent) -> list[str]:
    wanted = set(_display_names(intent.genres, t.media_type))
    out = [g for g in t.genres if g in wanted][:2]
    if intent.year_min and intent.year_max and intent.year_max - intent.year_min == 9 and t.year:
        out.append(f"{intent.year_min}s")
    return out


def _display_names(canonical: list[str], media_type: str) -> list[str]:
    table = genres.MOVIE if media_type == "movie" else genres.TV
    return [table[i] for i in genres.ids_for(canonical, media_type)]

