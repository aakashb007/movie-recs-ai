"""Thin async TMDB client with an in-memory TTL cache.

Accepts either a v3 API key or a v4 read-access token (the long one starting "eyJ").
"""

from __future__ import annotations

import asyncio
import time
from typing import Any

import httpx

from . import genres
from .schemas import CastMember, Provider, Providers, TitleDetail, TitleSummary

BASE_URL = "https://api.themoviedb.org/3"


class TMDBError(RuntimeError):
    pass


class TMDB:
    def __init__(self, api_key: str, client: httpx.AsyncClient, ttl: float = 6 * 3600, max_entries: int = 5000):
        self._client = client
        self._ttl = ttl
        self._max = max_entries
        self._cache: dict[str, tuple[float, Any]] = {}
        self._limit = asyncio.Semaphore(8)
        if api_key.startswith("eyJ"):
            self._headers, self._auth = {"Authorization": f"Bearer {api_key}"}, {}
        else:
            self._headers, self._auth = {}, {"api_key": api_key}

    async def _get(self, path: str, **params: Any) -> dict:
        params = {k: v for k, v in params.items() if v is not None}
        key = path + "?" + "&".join(f"{k}={params[k]}" for k in sorted(params))
        hit = self._cache.get(key)
        if hit and hit[0] > time.monotonic():
            return hit[1]
        async with self._limit:
            resp = await self._client.get(
                f"{BASE_URL}{path}", params={**params, **self._auth}, headers=self._headers, timeout=15
            )
        if resp.status_code == 404:
            raise TMDBError(f"not found: {path}")
        if resp.status_code >= 400:
            raise TMDBError(f"TMDB {resp.status_code} for {path}")
        body = resp.json()
        if len(self._cache) >= self._max:
            self._cache.clear()
        self._cache[key] = (time.monotonic() + self._ttl, body)
        return body

    async def trending(self, media_type: str, window: str = "week") -> list[TitleSummary]:
        body = await self._get(f"/trending/{media_type}/{window}")
        return [s for r in body.get("results", []) if (s := summary(r, media_type))]

    async def top_rated(self, media_type: str) -> list[TitleSummary]:
        body = await self._get(f"/{media_type}/top_rated")
        return [s for r in body.get("results", []) if (s := summary(r, media_type))]

    async def recommendations(self, media_type: str, tmdb_id: int) -> list[TitleSummary]:
        try:
            body = await self._get(f"/{media_type}/{tmdb_id}/recommendations")
        except TMDBError:
            return []
        return [s for r in body.get("results", []) if (s := summary(r, media_type))]

    async def summary(self, media_type: str, tmdb_id: int) -> TitleSummary | None:
        try:
            return summary(await self._details(media_type, tmdb_id), media_type)
        except TMDBError:
            return None

    async def _details(self, media_type: str, tmdb_id: int) -> dict:
        return await self._get(
            f"/{media_type}/{tmdb_id}", append_to_response="watch/providers,recommendations,credits,videos,keywords"
        )

    async def details(self, media_type: str, tmdb_id: int, region: str) -> TitleDetail:
        raw = await self._details(media_type, tmdb_id)
        base = summary(raw, media_type)
        if base is None:
            raise TMDBError("unusable title")
        region_providers = raw.get("watch/providers", {}).get("results", {}).get(region.upper(), {})

        def providers(kind: str) -> list[Provider]:
            return [Provider(name=p["provider_name"], logo_path=p.get("logo_path"))
                    for p in region_providers.get(kind, [])]

        videos = raw.get("videos", {}).get("results", [])
        trailer = next((v["key"] for v in videos if v.get("site") == "YouTube" and v.get("type") == "Trailer"), None)
        recs = raw.get("recommendations", {}).get("results", [])
        return TitleDetail(
            summary=base,
            tagline=raw.get("tagline") or "",
            runtime=raw.get("runtime") or (raw.get("episode_run_time") or [None])[0],
            seasons=raw.get("number_of_seasons"),
            status=raw.get("status") or "",
            cast=[CastMember(name=c["name"], character=c.get("character") or "", profile_path=c.get("profile_path"))
                  for c in raw.get("credits", {}).get("cast", [])[:10]],
            trailer_youtube_key=trailer,
            providers=Providers(
                link=region_providers.get("link"),
                stream=providers("flatrate"),
                free=providers("free") + providers("ads"),
                rent=providers("rent"),
                buy=providers("buy"),
            ),
            similar=[s for r in recs[:12] if (s := summary(r, media_type))],
        )

    async def keywords(self, media_type: str, tmdb_id: int) -> list[str]:
        try:
            raw = await self._details(media_type, tmdb_id)
        except TMDBError:
            return []
        block = raw.get("keywords", {})
        return [k["name"] for k in block.get("keywords", block.get("results", []))]

    async def search(self, query: str) -> list[TitleSummary]:
        body = await self._get("/search/multi", query=query, include_adult="false")
        return [s for r in body.get("results", []) if (s := summary(r, r.get("media_type", "")))]

    async def keyword_id(self, keyword: str) -> int | None:
        body = await self._get("/search/keyword", query=keyword)
        results = body.get("results", [])
        return results[0]["id"] if results else None

    async def discover(self, media_type: str, **params: Any) -> list[TitleSummary]:
        body = await self._get(f"/discover/{media_type}", include_adult="false", **params)
        return [s for r in body.get("results", []) if (s := summary(r, media_type))]


def summary(raw: dict, media_type: str) -> TitleSummary | None:
    """Normalise a TMDB movie/tv object (list item or details) into a TitleSummary."""
    if media_type not in ("movie", "tv") or "id" not in raw:
        return None
    title = raw.get("title") or raw.get("name")
    if not title:
        return None
    date = raw.get("release_date") or raw.get("first_air_date") or ""
    if "genre_ids" in raw:
        genre_names = genres.names(raw["genre_ids"], media_type)
    else:
        genre_names = [g["name"] for g in raw.get("genres", [])]
    return TitleSummary(
        id=raw["id"],
        media_type=media_type,
        title=title,
        year=int(date[:4]) if date[:4].isdigit() else None,
        overview=raw.get("overview") or "",
        poster_path=raw.get("poster_path"),
        backdrop_path=raw.get("backdrop_path"),
        vote_average=float(raw.get("vote_average") or 0.0),
        vote_count=int(raw.get("vote_count") or 0),
        popularity=float(raw.get("popularity") or 0.0),
        genres=genre_names,
        original_language=raw.get("original_language"),
    )
