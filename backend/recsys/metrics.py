"""Top-K ranking metrics. Every function takes ranked item lists per user."""

from __future__ import annotations

import itertools
import math

import numpy as np


def recall_at_k(recs: dict[int, list[int]], truth: dict[int, set[int]], k: int) -> float:
    """Hits in the top k divided by min(k, |relevant|), averaged over users with relevant items."""
    scores = [len(set(recs[u][:k]) & rel) / min(k, len(rel)) for u, rel in truth.items() if rel and u in recs]
    return float(np.mean(scores)) if scores else 0.0


def ndcg_at_k(recs: dict[int, list[int]], truth: dict[int, set[int]], k: int) -> float:
    """Binary-relevance NDCG@k, averaged over users with relevant items."""
    scores = []
    for u, rel in truth.items():
        if not rel or u not in recs:
            continue
        dcg = sum(1.0 / math.log2(i + 2) for i, item in enumerate(recs[u][:k]) if item in rel)
        ideal = sum(1.0 / math.log2(i + 2) for i in range(min(k, len(rel))))
        scores.append(dcg / ideal)
    return float(np.mean(scores)) if scores else 0.0


def catalog_coverage(recs: dict[int, list[int]], n_items: int, k: int) -> float:
    """Share of the catalog that appears in at least one user's top k."""
    shown = set(itertools.chain.from_iterable(r[:k] for r in recs.values()))
    return len(shown) / n_items


def intra_list_diversity(recs: dict[int, list[int]], item_vectors: np.ndarray, k: int) -> float:
    """Mean pairwise cosine distance between items in each top-k list (item_vectors must be L2-normalised)."""
    scores = []
    for r in recs.values():
        top = r[:k]
        if len(top) < 2:
            continue
        v = item_vectors[top]
        sim = v @ v.T
        n = len(top)
        scores.append(1.0 - (sim.sum() - np.trace(sim)) / (n * (n - 1)))
    return float(np.mean(scores)) if scores else 0.0


def novelty(recs: dict[int, list[int]], item_popularity: np.ndarray, k: int) -> float:
    """Mean self-information -log2(p(item)) of recommended items; higher means less mainstream."""
    p = item_popularity / item_popularity.sum()
    info = -np.log2(np.clip(p, 1e-12, None))
    scores = [info[r[:k]].mean() for r in recs.values() if r]
    return float(np.mean(scores)) if scores else 0.0
