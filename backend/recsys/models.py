"""Recommenders sharing one interface: fit on a training frame, then score any history.

Scoring from a raw history (items + ratings) rather than a trained user id means the
same code serves offline evaluation, cold-start simulation and live app users.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
import scipy.sparse as sp
from sklearn.decomposition import TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize


class Recommender:
    name = "base"

    def fit(self, train: pd.DataFrame, n_items: int) -> Recommender:
        raise NotImplementedError

    def score(self, items: np.ndarray, ratings: np.ndarray) -> np.ndarray:
        """Score every catalog item for a user with this history (higher is better)."""
        raise NotImplementedError

    def recommend(self, items: np.ndarray, ratings: np.ndarray, k: int) -> list[int]:
        scores = self.score(items, ratings).astype(np.float64)
        scores[items] = -np.inf
        top = np.argpartition(-scores, k)[:k]
        return top[np.argsort(-scores[top])].tolist()


class Popularity(Recommender):
    """Most-rated items. Strong on MovieLens; anything 'smarter' must beat it."""

    name = "popularity"

    def fit(self, train, n_items):
        self.counts = np.bincount(train.item, minlength=n_items).astype(np.float32)
        return self

    def score(self, items, ratings):
        return self.counts.copy()


@dataclass
class ItemKNN(Recommender):
    """Item-item cosine similarity on the binary interaction matrix, pruned to top neighbours."""

    neighbours: int = 50
    name: str = "item_knn"

    def fit(self, train, n_items):
        n_users = int(train.user.max()) + 1
        x = sp.csr_matrix(
            (np.ones(len(train), np.float32), (train.user, train.item)), shape=(n_users, n_items)
        )
        x = normalize(x, axis=0)
        sim = (x.T @ x).toarray()
        np.fill_diagonal(sim, 0.0)
        if self.neighbours < n_items:
            cutoff = -np.partition(-sim, self.neighbours, axis=1)[:, self.neighbours][:, None]
            sim[sim < cutoff] = 0.0
        self.sim = sim.astype(np.float32)
        return self

    def score(self, items, ratings):
        weights = ratings - 2.5  # liked items pull, disliked items push
        return weights @ self.sim[items]


@dataclass
class ALS(Recommender):
    """Implicit-feedback matrix factorisation (Hu, Koren & Volinsky 2008), solved exactly per row.

    Each rating is an observed interaction with confidence 1 + alpha * rating / 5.
    """

    factors: int = 64
    reg: float = 0.1
    alpha: float = 10.0
    iterations: int = 12
    seed: int = 0
    name: str = "als"

    def fit(self, train, n_items):
        rng = np.random.default_rng(self.seed)
        n_users = int(train.user.max()) + 1
        conf = 1.0 + self.alpha * train.rating.to_numpy(np.float32) / 5.0
        cui = sp.csr_matrix((conf, (train.user, train.item)), shape=(n_users, n_items))
        ciu = cui.T.tocsr()
        self.user_factors = rng.normal(0, 0.01, (n_users, self.factors)).astype(np.float64)
        self.item_factors = rng.normal(0, 0.01, (n_items, self.factors)).astype(np.float64)
        for _ in range(self.iterations):
            self._solve(cui, self.item_factors, self.user_factors)
            self._solve(ciu, self.user_factors, self.item_factors)
        self._solve(cui, self.item_factors, self.user_factors)  # users consistent with the final item factors
        self._gram = self.item_factors.T @ self.item_factors
        return self

    def _solve(self, conf: sp.csr_matrix, fixed: np.ndarray, out: np.ndarray) -> None:
        gram = fixed.T @ fixed + self.reg * np.eye(self.factors)
        for row in range(conf.shape[0]):
            lo, hi = conf.indptr[row], conf.indptr[row + 1]
            if lo == hi:
                out[row] = 0.0
                continue
            out[row] = self._fold(gram, fixed[conf.indices[lo:hi]], conf.data[lo:hi])

    @staticmethod
    def _fold(gram: np.ndarray, y: np.ndarray, c: np.ndarray) -> np.ndarray:
        a = gram + (y.T * (c - 1.0)) @ y
        b = (y * c[:, None]).sum(axis=0)
        return np.linalg.solve(a, b)

    def user_vector(self, items: np.ndarray, ratings: np.ndarray) -> np.ndarray:
        """Fold a new history into the learned item space without retraining."""
        if len(items) == 0:
            return np.zeros(self.factors)
        gram = self._gram + self.reg * np.eye(self.factors)
        conf = 1.0 + self.alpha * ratings.astype(np.float64) / 5.0
        return self._fold(gram, self.item_factors[items], conf)

    def score(self, items, ratings):
        return self.item_factors @ self.user_vector(items, ratings)


@dataclass
class Content(Recommender):
    """TF-IDF over item text reduced with truncated SVD; the user is a rating-weighted centroid."""

    dims: int = 64
    name: str = "content"
    item_vectors: np.ndarray = field(default=None, repr=False)

    def fit_text(self, texts: list[str]) -> Content:
        tfidf = TfidfVectorizer(min_df=2, sublinear_tf=True).fit_transform(texts)
        dims = max(2, min(self.dims, tfidf.shape[1] - 1, tfidf.shape[0] - 1))
        reduced = TruncatedSVD(dims, random_state=0).fit_transform(tfidf)
        self.item_vectors = normalize(reduced).astype(np.float32)
        return self

    def fit(self, train, n_items):
        if self.item_vectors is None:
            raise ValueError("call fit_text with item texts before fit")
        return self

    def score(self, items, ratings):
        if len(items) == 0:
            return np.zeros(len(self.item_vectors), np.float32)
        profile = (ratings - 3.0) @ self.item_vectors[items]
        norm = np.linalg.norm(profile)
        return self.item_vectors @ (profile / norm) if norm > 0 else np.zeros(len(self.item_vectors))


@dataclass
class Hybrid(Recommender):
    """Blend of z-scored collaborative (ALS + item-kNN), content and popularity scores.

    The collaborative weight grows with history length, n / (n + shrink): with few
    ratings it is mostly noise, so content similarity carries a new user. knn_share
    splits the collaborative weight between item-kNN and ALS. All three knobs are
    chosen on a validation split, never on test data.
    """

    als: ALS
    knn: ItemKNN
    content: Content
    popularity: Popularity
    shrink: float = 5.0
    knn_share: float = 0.5
    pop_weight: float = 0.25
    name: str = "hybrid"

    def fit(self, train, n_items):
        return self

    def score(self, items, ratings):
        n = len(items)
        cf = n / (n + self.shrink) if n else 0.0
        total = self.popularity_term()
        if cf > 0:
            if self.knn_share < 1:
                total = total + cf * (1 - self.knn_share) * _z(self.als.score(items, ratings))
            if self.knn_share > 0:
                total = total + cf * self.knn_share * _z(self.knn.score(items, ratings))
        if cf < 1:
            total = total + (1 - cf) * _z(self.content.score(items, ratings))
        return total

    def popularity_term(self) -> np.ndarray:
        if not hasattr(self, "_pop_z"):
            self._pop_z = _z(self.popularity.counts)
        return self.pop_weight * self._pop_z


def _z(x: np.ndarray) -> np.ndarray:
    std = x.std()
    return (x - x.mean()) / std if std > 0 else np.zeros_like(x, dtype=np.float64)


def mmr(candidates: list[int], relevance: np.ndarray, vectors: np.ndarray, k: int, lam: float = 0.8) -> list[int]:
    """Maximal Marginal Relevance: greedily trade relevance against similarity to items already picked.

    relevance is aligned with candidates; vectors are L2-normalised and indexed by item id.
    """
    if not candidates:
        return []
    rel = np.asarray(relevance, dtype=np.float64)
    span = rel.max() - rel.min()
    rel = (rel - rel.min()) / span if span > 0 else np.ones_like(rel)
    vecs = vectors[candidates]
    picked: list[int] = []
    max_sim = np.zeros(len(candidates))
    available = np.ones(len(candidates), bool)
    for _ in range(min(k, len(candidates))):
        gain = np.where(available, lam * rel - (1 - lam) * max_sim, -np.inf)
        best = int(np.argmax(gain))
        picked.append(best)
        available[best] = False
        max_sim = np.maximum(max_sim, vecs @ vecs[best])
    return [candidates[i] for i in picked]


class Reranked(Recommender):
    """Any recommender followed by MMR over its top candidates."""

    def __init__(self, base: Recommender, vectors: np.ndarray, lam: float = 0.8, pool: int = 100):
        self.base, self.vectors, self.lam, self.pool = base, vectors, lam, pool
        self.name = f"{base.name}+mmr"

    def recommend(self, items, ratings, k):
        scores = self.base.score(items, ratings).astype(np.float64)
        scores[items] = -np.inf
        pool = np.argpartition(-scores, self.pool)[: self.pool]
        pool = pool[np.argsort(-scores[pool])]
        return mmr(pool.tolist(), scores[pool], self.vectors, k, self.lam)
