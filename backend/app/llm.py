"""Turning a free-text request into a structured QueryIntent.

The LLM (Groq, OpenAI-compatible API) only fills in filters. It never chooses result
titles: candidates always come from TMDB, so it cannot recommend something that
does not exist. Without a GROQ_API_KEY a rule-based parser does the same job, less well.
"""

from __future__ import annotations

import json
import logging
import re

import httpx
from pydantic import ValidationError

from . import genres
from .schemas import QueryIntent

log = logging.getLogger(__name__)

GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"

SYSTEM_PROMPT = f"""You convert a person's request for something to watch into JSON filters.
Return only a JSON object with these keys:
  media_type: "movie", "tv" or "any"
  genres: list from {sorted(genres.CANONICAL)}
  exclude_genres: same vocabulary
  keywords: up to 4 short themes or subjects, e.g. "time travel", "heist", "small town"
  reference_titles: titles the person compares to ("like Dark" -> ["Dark"]); never invent titles they did not name
  year_min, year_max: integers or null ("90s" -> 1990, 1999)
  min_rating: 0-10 or null (only when they ask for acclaimed/highly rated: 7.0)
  language: ISO 639-1 code or null (only if they ask for a language or country)
  summary: one short sentence restating the request
Map moods to genres and keywords ("something cosy" -> comedy, family, keywords ["feel good"]).
"""


async def parse_with_groq(query: str, api_key: str, model: str, client: httpx.AsyncClient) -> QueryIntent:
    resp = await client.post(
        GROQ_URL,
        headers={"Authorization": f"Bearer {api_key}"},
        json={
            "model": model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [{"role": "system", "content": SYSTEM_PROMPT}, {"role": "user", "content": query}],
        },
        timeout=20,
    )
    resp.raise_for_status()
    content = resp.json()["choices"][0]["message"]["content"]
    return sanitize(QueryIntent.model_validate(json.loads(content)), query)


async def parse(query: str, api_key: str | None, model: str, client: httpx.AsyncClient) -> tuple[QueryIntent, str]:
    """Returns the intent and which parser produced it ("llm" or "rules")."""
    if api_key:
        try:
            return await parse_with_groq(query, api_key, model, client), "llm"
        except (httpx.HTTPError, KeyError, ValueError, ValidationError) as exc:
            log.warning("groq parse failed, falling back to rules: %s", exc)
    return parse_with_rules(query), "rules"


def sanitize(intent: QueryIntent, query: str) -> QueryIntent:
    """Clamp everything the model said to the vocabulary and ranges the rest of the system understands."""

    def canon(values: list[str]) -> list[str]:
        out = []
        for v in values:
            c = genres.canonicalize(str(v))
            if c and c not in out:
                out.append(c)
        return out[:5]

    def year(y: int | None) -> int | None:
        return y if y is None or 1900 <= y <= 2035 else None

    lang = intent.language.lower() if intent.language and re.fullmatch(r"[a-zA-Z]{2}", intent.language) else None
    rating = intent.min_rating if intent.min_rating is not None and 0 <= intent.min_rating <= 10 else None
    excluded = canon(intent.exclude_genres)
    return QueryIntent(
        media_type=intent.media_type,
        genres=[g for g in canon(intent.genres) if g not in excluded],
        exclude_genres=excluded,
        keywords=[k.strip()[:40] for k in intent.keywords if k.strip()][:4],
        reference_titles=[t.strip()[:80] for t in intent.reference_titles if t.strip()][:3],
        year_min=year(intent.year_min),
        year_max=year(intent.year_max),
        min_rating=rating,
        language=lang,
        summary=(intent.summary or query)[:200],
    )


_DECADE = re.compile(r"\b(?:(19|20)?(\d)0)'?s\b")
_LIKE = re.compile(r"\b(?:like|similar to|such as)\s+(.+?)(?:\s+(?:but|and|with|from|in|that|which)\b|[,.!?]|$)", re.I)
_NEGATED = re.compile(r"\b(?:no|not|without|nothing)\s+([a-z\- ]+?)(?:\b(?:and|but|or)\b|[,.!?]|$)")


def parse_with_rules(query: str) -> QueryIntent:
    text = query.lower()
    media = "any"
    if re.search(r"\b(show|shows|series|tv|season|binge)\b", text):
        media = "tv"
    elif re.search(r"\b(movie|movies|film|films)\b", text):
        media = "movie"

    excluded: list[str] = []
    for m in _NEGATED.finditer(text):
        for word in re.split(r"\s+", m.group(1).strip()):
            if (c := genres.canonicalize(word)) and c not in excluded:
                excluded.append(c)
    found = []
    for term in list(genres.SYNONYMS) + list(genres.CANONICAL):
        if re.search(rf"\b{re.escape(term)}\b", text):
            c = genres.canonicalize(term)
            if c and c not in found and c not in excluded:
                found.append(c)

    year_min = year_max = None
    if m := _DECADE.search(text):
        century = m.group(1) or ("20" if m.group(2) in "012" else "19")
        year_min = int(f"{century}{m.group(2)}0")
        year_max = year_min + 9
    if re.search(r"\b(recent|new|latest)\b", text):
        year_min = 2020
    min_rating = 7.0 if re.search(r"\b(acclaimed|best|great|top rated|highly rated|masterpiece)\b", text) else None

    refs = [m.group(1).strip(" '\"") for m in _LIKE.finditer(query)]
    return sanitize(
        QueryIntent(media_type=media, genres=found, exclude_genres=excluded, reference_titles=refs,
                    year_min=year_min, year_max=year_max, min_rating=min_rating, summary=query),
        query,
    )
