"""TMDB genre ids. Movies and TV use different ids for some genres, so the
LLM and the app speak in canonical names and this module translates."""

from __future__ import annotations

MOVIE = {
    28: "Action", 12: "Adventure", 16: "Animation", 35: "Comedy", 80: "Crime", 99: "Documentary",
    18: "Drama", 10751: "Family", 14: "Fantasy", 36: "History", 27: "Horror", 10402: "Music",
    9648: "Mystery", 10749: "Romance", 878: "Science Fiction", 10770: "TV Movie", 53: "Thriller",
    10752: "War", 37: "Western",
}
TV = {
    10759: "Action & Adventure", 16: "Animation", 35: "Comedy", 80: "Crime", 99: "Documentary",
    18: "Drama", 10751: "Family", 10762: "Kids", 9648: "Mystery", 10763: "News", 10764: "Reality",
    10765: "Sci-Fi & Fantasy", 10766: "Soap", 10767: "Talk", 10768: "War & Politics", 37: "Western",
}

# canonical name -> (movie ids, tv ids)
CANONICAL: dict[str, tuple[list[int], list[int]]] = {
    "action": ([28], [10759]),
    "adventure": ([12], [10759]),
    "animation": ([16], [16]),
    "comedy": ([35], [35]),
    "crime": ([80], [80]),
    "documentary": ([99], [99]),
    "drama": ([18], [18]),
    "family": ([10751], [10751]),
    "fantasy": ([14], [10765]),
    "history": ([36], []),
    "horror": ([27], []),
    "music": ([10402], []),
    "mystery": ([9648], [9648]),
    "romance": ([10749], []),
    "science fiction": ([878], [10765]),
    "thriller": ([53], []),
    "war": ([10752], [10768]),
    "western": ([37], [37]),
    "kids": ([], [10762]),
    "reality": ([], [10764]),
}

SYNONYMS = {
    "sci-fi": "science fiction", "scifi": "science fiction", "sci fi": "science fiction", "space": "science fiction",
    "funny": "comedy", "comedies": "comedy", "laugh": "comedy", "scary": "horror", "spooky": "horror",
    "romantic": "romance", "rom-com": "romance", "love story": "romance", "animated": "animation",
    "anime": "animation", "cartoon": "animation", "documentaries": "documentary", "docs": "documentary",
    "thrillers": "thriller", "suspense": "thriller", "detective": "mystery", "whodunit": "mystery",
    "heist": "crime", "gangster": "crime", "magic": "fantasy", "historical": "history", "musical": "music",
    "cowboy": "western", "children": "kids",
}


def names(ids: list[int], media_type: str) -> list[str]:
    table = MOVIE if media_type == "movie" else TV
    return [table[i] for i in ids if i in table]


def ids_for(canonical: list[str], media_type: str) -> list[int]:
    slot = 0 if media_type == "movie" else 1
    out: list[int] = []
    for name in canonical:
        for gid in CANONICAL.get(name, ([], []))[slot]:
            if gid not in out:
                out.append(gid)
    return out


def canonicalize(name: str) -> str | None:
    key = name.strip().lower()
    key = SYNONYMS.get(key, key)
    return key if key in CANONICAL else None
