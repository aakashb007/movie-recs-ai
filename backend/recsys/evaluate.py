"""Offline evaluation against baselines.

Two experiments:
  warm  - every user's most recent 20% of ratings is held out; models train on the rest.
  cold  - 20% of users are removed from training entirely; each is scored from only
          their first few ratings, the situation of a person who just onboarded.

Hyper-parameters are tuned on a second temporal split carved out of the training data,
so the test period is never looked at until the final numbers.

Usage:
  python -m recsys.evaluate --dataset ml-latest-small --out reports
  python -m recsys.evaluate --synthetic --out reports
"""

from __future__ import annotations

import argparse
import itertools
import json
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd

from . import data, synthetic
from .metrics import catalog_coverage, intra_list_diversity, ndcg_at_k, novelty, recall_at_k
from .models import ALS, Content, Hybrid, ItemKNN, Popularity, Recommender, Reranked
from .split import relevant_items, temporal_user_split

K = 10
ALS_GRID = {"factors": [32, 64], "reg": [0.1, 1.0], "alpha": [5.0, 20.0]}
HYBRID_GRID = {"shrink": [2.0, 10.0, 40.0], "knn_share": [0.0, 0.5, 1.0], "pop_weight": [0.0, 0.25, 0.5]}


def histories(frame: pd.DataFrame) -> dict[int, tuple[np.ndarray, np.ndarray]]:
    ordered = frame.sort_values(["user", "timestamp"], kind="stable")
    return {
        int(u): (g.item.to_numpy(np.int64), g.rating.to_numpy(np.float32)) for u, g in ordered.groupby("user")
    }


def run(model: Recommender, hist: dict, truth: dict[int, set[int]], k: int = K) -> dict[int, list[int]]:
    return {u: model.recommend(*hist[u], k) for u in truth if u in hist}


def score(recs, truth, n_items, vectors, pop) -> dict[str, float]:
    return {
        f"ndcg@{K}": ndcg_at_k(recs, truth, K),
        f"recall@{K}": recall_at_k(recs, truth, K),
        "coverage": catalog_coverage(recs, n_items, K),
        "diversity": intra_list_diversity(recs, vectors, K),
        "novelty": novelty(recs, pop, K),
    }


def tune(train: pd.DataFrame, n_items: int, content: Content) -> tuple[dict, dict]:
    fit_part, val = temporal_user_split(train)
    val_truth, val_hist = relevant_items(val), histories(fit_part)
    pop = Popularity().fit(fit_part, n_items)
    knn = ItemKNN().fit(fit_part, n_items)

    best_als, best_ndcg, best_model = None, -1.0, None
    for values in itertools.product(*ALS_GRID.values()):
        params = dict(zip(ALS_GRID, values, strict=True))
        model = ALS(**params).fit(fit_part, n_items)
        ndcg = ndcg_at_k(run(model, val_hist, val_truth), val_truth, K)
        log(f"  als {params} val ndcg={ndcg:.4f}")
        if ndcg > best_ndcg:
            best_als, best_ndcg, best_model = params, ndcg, model

    best_hybrid, best_ndcg = None, -1.0
    for values in itertools.product(*HYBRID_GRID.values()):
        params = dict(zip(HYBRID_GRID, values, strict=True))
        model = Hybrid(best_model, knn, content, pop, **params)
        ndcg = ndcg_at_k(run(model, val_hist, val_truth), val_truth, K)
        log(f"  hybrid {params} val ndcg={ndcg:.4f}")
        if ndcg > best_ndcg:
            best_hybrid, best_ndcg = params, ndcg
    return best_als, best_hybrid


def build_models(train, n_items, content, als_params, hybrid_params) -> list[Recommender]:
    pop = Popularity().fit(train, n_items)
    als = ALS(**als_params).fit(train, n_items)
    knn = ItemKNN().fit(train, n_items)
    hybrid = Hybrid(als, knn, content, pop, **hybrid_params)
    return [pop, knn, als, content, hybrid, Reranked(hybrid, content.item_vectors)]


def evaluate(ds: data.Dataset, cold_history: int = 5, seed: int = 0) -> dict:
    content = Content().fit_text(ds.items.text.fillna("").tolist())
    vectors = content.item_vectors
    train, test = temporal_user_split(ds.ratings)
    log("tuning on validation split")
    als_params, hybrid_params = tune(train, ds.n_items, content)
    log(f"chosen als={als_params} hybrid={hybrid_params}")

    results: dict = {"params": {"als": als_params, "hybrid": hybrid_params}, "warm": {}, "cold": {}}
    truth, hist = relevant_items(test), histories(train)
    pop_counts = np.bincount(train.item, minlength=ds.n_items) + 1
    for model in build_models(train, ds.n_items, content, als_params, hybrid_params):
        results["warm"][model.name] = score(run(model, hist, truth), truth, ds.n_items, vectors, pop_counts)
    results["warm_users"] = len(truth)

    rng = np.random.default_rng(seed)
    users = ds.ratings.user.unique()
    cold_users = set(rng.choice(users, size=max(1, len(users) // 5), replace=False).tolist())
    in_cold = ds.ratings.user.isin(cold_users)
    warm_train = ds.ratings[~in_cold]
    cold_frame = ds.ratings[in_cold].sort_values(["user", "timestamp"], kind="stable")
    rank = cold_frame.groupby("user").cumcount()
    cold_hist = histories(cold_frame[rank < cold_history])
    cold_truth = relevant_items(cold_frame[rank >= cold_history])
    for model in build_models(warm_train, ds.n_items, content, als_params, hybrid_params):
        results["cold"][model.name] = score(
            run(model, cold_hist, cold_truth), cold_truth, ds.n_items, vectors, pop_counts
        )
    results["cold_users"] = len(cold_truth)
    results["cold_history"] = cold_history
    return results


def to_markdown(results: dict, dataset: str) -> str:
    lines = [f"# Offline evaluation: {dataset}", ""]
    lines.append(f"Chosen on validation: ALS `{results['params']['als']}`, hybrid `{results['params']['hybrid']}`.")
    for exp, title in [("warm", "Warm users (latest 20% of each history held out)"),
                       ("cold", f"Cold-start users (unseen, {results['cold_history']} ratings given)")]:
        lines += ["", f"## {title}", "", f"Users evaluated: {results[exp + '_users']}", ""]
        metrics = list(next(iter(results[exp].values())))
        lines.append("| model | " + " | ".join(metrics) + " |")
        lines.append("|---" * (len(metrics) + 1) + "|")
        for name, row in results[exp].items():
            lines.append(f"| {name} | " + " | ".join(f"{row[m]:.4f}" for m in metrics) + " |")
    return "\n".join(lines) + "\n"


def log(msg: str) -> None:
    print(msg, file=sys.stderr, flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="ml-latest-small", choices=list(data.MOVIELENS_URLS))
    parser.add_argument("--synthetic", action="store_true")
    parser.add_argument("--data-dir", type=Path, default=Path("data"))
    parser.add_argument("--out", type=Path, default=Path("reports"))
    parser.add_argument("--require-hybrid-wins", action="store_true",
                        help="exit 1 unless the hybrid beats both baselines on warm NDCG@10")
    args = parser.parse_args()

    start = time.time()
    if args.synthetic:
        name, ds = "synthetic", synthetic.make()
    else:
        name, ds = args.dataset, data.load(data.download(args.dataset, args.data_dir))
    log(f"{name}: {ds.n_users} users, {ds.n_items} items, {len(ds.ratings)} ratings")
    results = evaluate(ds)
    results["dataset"] = name
    results["seconds"] = round(time.time() - start, 1)

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f"eval-{name}.json").write_text(json.dumps(results, indent=2))
    report = to_markdown(results, name)
    (args.out / f"eval-{name}.md").write_text(report)
    print(report)

    if args.require_hybrid_wins:
        warm = results["warm"]
        best = max(("hybrid", "hybrid+mmr"), key=lambda m: warm[m][f"ndcg@{K}"])
        baseline = max(warm["popularity"][f"ndcg@{K}"], warm["item_knn"][f"ndcg@{K}"])
        if warm[best][f"ndcg@{K}"] <= baseline:
            log(f"FAIL: {best} ndcg@{K}={warm[best][f'ndcg@{K}']:.4f} does not beat best baseline {baseline:.4f}")
            return 1
        log(f"OK: {best} beats best baseline ({warm[best][f'ndcg@{K}']:.4f} > {baseline:.4f})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
