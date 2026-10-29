"""Loading MovieLens data.

MovieLens is licensed by GroupLens for research use and may not be redistributed,
so it is downloaded at build time and never committed (see .gitignore).
"""

from __future__ import annotations

import io
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

MOVIELENS_URLS = {
    "ml-latest-small": "https://files.grouplens.org/datasets/movielens/ml-latest-small.zip",
    "ml-32m": "https://files.grouplens.org/datasets/movielens/ml-32m.zip",
}


@dataclass
class Dataset:
    """Ratings re-indexed to dense integer ids, plus per-item metadata."""

    ratings: pd.DataFrame  # columns: user, item, rating, timestamp
    items: pd.DataFrame  # index: item; columns: movie_id, tmdb_id, title, year, genres, text
    n_users: int
    n_items: int


def download(name: str, dest: Path) -> Path:
    """Download and unzip a MovieLens release into dest; returns the extracted folder."""
    target = dest / name
    if (target / "ratings.csv").exists():
        return target
    dest.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(MOVIELENS_URLS[name], timeout=300) as resp:
        payload = resp.read()
    with zipfile.ZipFile(io.BytesIO(payload)) as zf:
        zf.extractall(dest)
    return target


def load(folder: Path, min_item_ratings: int = 5) -> Dataset:
    """Load ratings, movies, links and tags; keep items with at least min_item_ratings ratings."""
    ratings = pd.read_csv(folder / "ratings.csv")
    movies = pd.read_csv(folder / "movies.csv")
    links = pd.read_csv(folder / "links.csv")
    tags_path = folder / "tags.csv"
    tags = pd.read_csv(tags_path) if tags_path.exists() else pd.DataFrame(columns=["movieId", "tag"])

    counts = ratings.groupby("movieId").size()
    keep = counts[counts >= min_item_ratings].index
    ratings = ratings[ratings.movieId.isin(keep)]

    user_codes, user_index = pd.factorize(ratings.userId, sort=True)
    item_codes, item_index = pd.factorize(ratings.movieId, sort=True)
    frame = pd.DataFrame(
        {
            "user": user_codes.astype(np.int32),
            "item": item_codes.astype(np.int32),
            "rating": ratings.rating.to_numpy(np.float32),
            "timestamp": ratings.timestamp.to_numpy(np.int64),
        }
    )

    meta = pd.DataFrame({"movie_id": item_index}).merge(
        movies.rename(columns={"movieId": "movie_id"}), on="movie_id", how="left"
    )
    meta = meta.merge(links.rename(columns={"movieId": "movie_id", "tmdbId": "tmdb_id"}), on="movie_id", how="left")
    meta["year"] = meta.title.str.extract(r"\((\d{4})\)\s*$")[0].astype(float)
    tag_text = (
        tags.assign(tag=tags.tag.astype(str).str.lower())
        .groupby("movieId")
        .tag.apply(lambda t: " ".join(t))
        .rename("tags")
    )
    meta = meta.merge(tag_text, left_on="movie_id", right_index=True, how="left")
    meta["text"] = meta.apply(_content_text, axis=1)
    meta.index.name = "item"

    return Dataset(
        ratings=frame,
        items=meta[["movie_id", "tmdb_id", "title", "year", "genres", "text"]],
        n_users=len(user_index),
        n_items=len(item_index),
    )


def _content_text(row: pd.Series) -> str:
    """Text the content model embeds: genres (repeated to weigh them), decade, user tags."""
    genres = "" if pd.isna(row.genres) else row.genres.replace("|", " ").replace("-", "")
    decade = "" if pd.isna(row.year) else f"decade{int(row.year) // 10 * 10}"
    tags = "" if pd.isna(row.get("tags")) else row.tags
    return f"{genres} {genres} {decade} {tags}".strip()
