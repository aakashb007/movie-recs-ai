"""Temporal splits: models only ever see a user's past when predicting their future."""

from __future__ import annotations

import numpy as np
import pandas as pd


def temporal_user_split(
    ratings: pd.DataFrame, test_frac: float = 0.2, min_ratings: int = 10
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Hold out each user's most recent test_frac of ratings.

    Users with fewer than min_ratings ratings stay entirely in train. Within a user,
    ties on timestamp are broken by row order so the split is deterministic.
    """
    ordered = ratings.sort_values(["user", "timestamp"], kind="stable")
    size = ordered.groupby("user").item.transform("size")
    rank = ordered.groupby("user").cumcount()
    n_test = np.floor(size * test_frac).astype(int)
    is_test = (size >= min_ratings) & (rank >= size - n_test)
    return ordered[~is_test].copy(), ordered[is_test].copy()


def relevant_items(test: pd.DataFrame, threshold: float = 4.0) -> dict[int, set[int]]:
    """Items a user rated at or above threshold in the held-out period: what a good model should surface."""
    liked = test[test.rating >= threshold]
    return {int(u): set(map(int, g.item)) for u, g in liked.groupby("user")}
