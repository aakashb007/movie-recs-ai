"""An in-memory TMDB + Groq stand-in served through httpx.MockTransport."""

from __future__ import annotations

import json
import re

import httpx

MOVIE_GENRES = {"scifi": 878, "comedy": 35, "horror": 27, "drama": 18, "crime": 80}
TV_GENRES = {"scifi": 10765, "comedy": 35, "drama": 18, "crime": 80}


def _catalog() -> dict[tuple[str, int], dict]:
    rows = {}
    themes = ["scifi", "comedy", "horror", "drama", "crime"]
    for i in range(1, 41):
        theme = themes[i % len(themes)]
        rows[("movie", i)] = {
            "id": i, "title": f"Film {i}", "release_date": f"{1985 + i}-05-01",
            "genre_ids": [MOVIE_GENRES[theme]], "overview": f"A {theme} story about {theme} things number {i}",
            "poster_path": f"/m{i}.jpg", "vote_average": 6 + (i % 4), "vote_count": 500 + i * 10,
            "popularity": 100 - i, "original_language": "en",
        }
    for i in range(101, 121):
        theme = ["scifi", "comedy", "drama", "crime"][i % 4]
        rows[("tv", i)] = {
            "id": i, "name": f"Show {i}", "first_air_date": f"{1990 + i - 100}-01-01",
            "genre_ids": [TV_GENRES[theme]], "overview": f"A {theme} series about {theme} people {i}",
            "poster_path": f"/t{i}.jpg", "vote_average": 7.5, "vote_count": 900, "popularity": 50,
            "original_language": "de" if i == 105 else "en",
        }
    return rows


class FakeServices:
    def __init__(self, groq_reply: dict | None = None, groq_status: int = 200):
        self.catalog = _catalog()
        self.groq_reply = groq_reply or {}
        self.groq_status = groq_status
        self.calls: list[str] = []

    def ids(self) -> set[tuple[str, int]]:
        return set(self.catalog)

    def _list(self, mt: str, rows: list[dict]) -> httpx.Response:
        return httpx.Response(200, json={"results": rows})

    def _theme_of(self, row: dict) -> int:
        return row["genre_ids"][0]

    def handler(self, request: httpx.Request) -> httpx.Response:
        url = request.url
        self.calls.append(f"{request.method} {url.host}{url.path}")
        if url.host == "api.groq.com":
            if self.groq_status != 200:
                return httpx.Response(self.groq_status, json={"error": "boom"})
            return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(self.groq_reply)}}]})

        path = url.path.removeprefix("/3")
        q = dict(url.params)
        if m := re.fullmatch(r"/trending/(movie|tv)/week", path):
            mt = m.group(1)
            return self._list(mt, [r for (t, _), r in self.catalog.items() if t == mt][:20])
        if m := re.fullmatch(r"/(movie|tv)/top_rated", path):
            mt = m.group(1)
            rows = sorted((r for (t, _), r in self.catalog.items() if t == mt), key=lambda r: -r["vote_average"])
            return self._list(mt, rows[:20])
        if m := re.fullmatch(r"/(movie|tv)/(\d+)/recommendations", path):
            mt, tid = m.group(1), int(m.group(2))
            base = self.catalog.get((mt, tid))
            if base is None:
                return httpx.Response(404)
            rows = [r for (t, i), r in self.catalog.items()
                    if t == mt and i != tid and self._theme_of(r) == self._theme_of(base)]
            return self._list(mt, rows)
        if m := re.fullmatch(r"/(movie|tv)/(\d+)", path):
            mt, tid = m.group(1), int(m.group(2))
            row = self.catalog.get((mt, tid))
            if row is None:
                return httpx.Response(404)
            genre_names = {878: "Science Fiction", 35: "Comedy", 27: "Horror", 18: "Drama", 80: "Crime",
                           10765: "Sci-Fi & Fantasy"}
            detail = {k: v for k, v in row.items() if k != "genre_ids"}
            detail["genres"] = [{"id": g, "name": genre_names[g]} for g in row["genre_ids"]]
            detail["watch/providers"] = {"results": {"GB": {"link": "https://example.test/watch",
                                                            "flatrate": [{"provider_name": "StreamCo",
                                                                          "logo_path": "/s.png"}]}}}
            detail["videos"] = {"results": [{"site": "YouTube", "type": "Trailer", "key": "abc123"}]}
            detail["credits"] = {"cast": [{"name": "Ada Actor", "character": "Lead"}]}
            detail["recommendations"] = {"results": []}
            detail["runtime"] = 120 if mt == "movie" else None
            return httpx.Response(200, json=detail)
        if path == "/search/multi":
            needle = q.get("query", "").lower()
            rows = [{**r, "media_type": t} for (t, _), r in self.catalog.items()
                    if needle and needle in (r.get("title") or r.get("name")).lower()]
            return self._list("multi", rows)
        if path == "/search/keyword":
            return self._list("kw", [{"id": 999, "name": q.get("query")}] if q.get("query") == "time travel" else [])
        if m := re.fullmatch(r"/discover/(movie|tv)", path):
            mt = m.group(1)
            rows = list(r for (t, _), r in self.catalog.items() if t == mt)
            if g := q.get("with_genres"):
                wanted = {int(x) for x in re.split(r"[,|]", g)}
                rows = [r for r in rows if wanted & set(r["genre_ids"])]
            if g := q.get("without_genres"):
                banned = {int(x) for x in g.split(",")}
                rows = [r for r in rows if not banned & set(r["genre_ids"])]
            date_key = "release_date" if mt == "movie" else "first_air_date"
            field = "primary_release_date" if mt == "movie" else "first_air_date"
            if lo := q.get(f"{field}.gte"):
                rows = [r for r in rows if r[date_key] >= lo]
            if hi := q.get(f"{field}.lte"):
                rows = [r for r in rows if r[date_key] <= hi]
            if lang := q.get("with_original_language"):
                rows = [r for r in rows if r["original_language"] == lang]
            return self._list(mt, rows[:20])
        return httpx.Response(404)

    def transport(self) -> httpx.MockTransport:
        return httpx.MockTransport(self.handler)
