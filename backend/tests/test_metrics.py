import math

import numpy as np
import pandas as pd

from recsys.metrics import catalog_coverage, intra_list_diversity, ndcg_at_k, novelty, recall_at_k
from recsys.split import relevant_items, temporal_user_split


def test_ndcg_and_recall_hand_computed():
    recs = {0: [5, 1, 7], 1: [3, 4, 9]}
    truth = {0: {1, 2}, 1: {8}}
    assert recall_at_k(recs, truth, 3) == (1 / 2 + 0) / 2
    expected_user0 = (1 / math.log2(3)) / (1 + 1 / math.log2(3))
    assert math.isclose(ndcg_at_k(recs, truth, 3), expected_user0 / 2)


def test_perfect_ranking_scores_one():
    assert ndcg_at_k({0: [1, 2]}, {0: {1, 2}}, 2) == 1.0
    assert recall_at_k({0: [1, 2, 3]}, {0: {1, 2}}, 3) == 1.0


def test_coverage_diversity_novelty():
    recs = {0: [0, 1], 1: [0, 2]}
    assert catalog_coverage(recs, 10, 2) == 0.3
    same = np.array([[1.0, 0.0], [1.0, 0.0], [1.0, 0.0]])
    orth = np.array([[1.0, 0.0], [0.0, 1.0], [0.0, 1.0]])
    assert intra_list_diversity({0: [0, 1]}, same, 2) == 0.0
    assert intra_list_diversity({0: [0, 1]}, orth, 2) == 1.0
    pop = np.array([100.0, 1.0, 1.0])
    assert novelty({0: [1]}, pop, 1) > novelty({0: [0]}, pop, 1)


def test_temporal_split_holds_out_latest_ratings():
    frame = pd.DataFrame({
        "user": [0] * 10 + [1] * 3,
        "item": list(range(10)) + [0, 1, 2],
        "rating": [4.0] * 13,
        "timestamp": list(range(10, 0, -1)) + [1, 2, 3],
    })
    train, test = temporal_user_split(frame, test_frac=0.2, min_ratings=5)
    assert set(test.user) == {0}  # user 1 is too short to split
    assert set(test.item) == {0, 1}  # items with the latest timestamps
    assert train.groupby("user").timestamp.max()[0] < test.timestamp.min()
    assert relevant_items(test) == {0: {0, 1}}
