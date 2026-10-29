"""Fit the serving model on all MovieLens ratings and save it for the API.

Hyper-parameters come from the evaluation report so serving uses exactly the
configuration that was validated offline.

Usage: python -m recsys.train --dataset ml-latest-small --out artifacts/model.pkl
"""

from __future__ import annotations

import argparse
import json
import pickle
from pathlib import Path

from . import data
from .models import ALS, Content, Hybrid, ItemKNN, Popularity

DEFAULT_PARAMS = {"als": {"factors": 64, "reg": 1.0, "alpha": 20.0}, "hybrid": {"shrink": 10.0, "knn_share": 0.5}}


def build_bundle(ds: data.Dataset, params: dict) -> dict:
    content = Content().fit_text(ds.items.text.fillna("").tolist())
    pop = Popularity().fit(ds.ratings, ds.n_items)
    model = Hybrid(
        ALS(**params["als"]).fit(ds.ratings, ds.n_items),
        ItemKNN().fit(ds.ratings, ds.n_items),
        content,
        pop,
        **params["hybrid"],
    )
    items = ds.items.dropna(subset=["tmdb_id"])
    return {
        "model": model,
        "tmdb_to_item": {int(t): int(i) for i, t in items.tmdb_id.items()},
        "item_to_tmdb": {int(i): int(t) for i, t in items.tmdb_id.items()},
        "params": params,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="ml-latest-small", choices=list(data.MOVIELENS_URLS))
    parser.add_argument("--data-dir", type=Path, default=Path("data"))
    parser.add_argument("--params", type=Path, default=Path("reports/eval-ml-latest-small.json"))
    parser.add_argument("--out", type=Path, default=Path("artifacts/model.pkl"))
    args = parser.parse_args()

    params = json.loads(args.params.read_text())["params"] if args.params.exists() else DEFAULT_PARAMS
    ds = data.load(data.download(args.dataset, args.data_dir))
    bundle = build_bundle(ds, params)
    bundle["dataset"] = args.dataset
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("wb") as f:
        pickle.dump(bundle, f, protocol=pickle.HIGHEST_PROTOCOL)
    print(f"saved {args.out} ({ds.n_items} items, params={params})")


if __name__ == "__main__":
    main()
