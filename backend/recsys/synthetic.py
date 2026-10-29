"""A small MovieLens-shaped dataset with planted structure, for tests and offline smoke runs."""

from __future__ import annotations

import numpy as np
import pandas as pd

from .data import Dataset

GENRES = ["Action", "Comedy", "Drama", "Horror", "SciFi", "Romance", "Thriller", "Animation"]


def make(n_users: int = 300, n_items: int = 400, per_user: int = 40, seed: int = 0) -> Dataset:
    rng = np.random.default_rng(seed)
    item_genre = rng.integers(0, len(GENRES), n_items)
    item_pop = rng.pareto(1.5, n_items) + 1
    taste = rng.dirichlet(np.full(len(GENRES), 0.3), n_users)

    rows = []
    for u in range(n_users):
        affinity = taste[u][item_genre] * item_pop
        chosen = rng.choice(n_items, size=per_user, replace=False, p=affinity / affinity.sum())
        for t, i in enumerate(chosen):
            rating = np.clip(np.round(2 + 6 * taste[u][item_genre[i]] + rng.normal(0, 0.7)), 1, 5)
            rows.append((u, int(i), float(rating), 1_000_000 + u * 1000 + t))
    ratings = pd.DataFrame(rows, columns=["user", "item", "rating", "timestamp"])

    items = pd.DataFrame(
        {
            "movie_id": np.arange(n_items),
            "tmdb_id": np.arange(n_items) + 10_000,
            "title": [f"Movie {i}" for i in range(n_items)],
            "year": 1990 + rng.integers(0, 35, n_items),
            "genres": [GENRES[g] for g in item_genre],
            "text": [f"{GENRES[g]} {GENRES[g]} {GENRES[g].lower()}story" for g in item_genre],
        }
    )
    items.index.name = "item"
    return Dataset(ratings=ratings, items=items, n_users=n_users, n_items=n_items)
