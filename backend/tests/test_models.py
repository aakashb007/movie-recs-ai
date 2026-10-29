import numpy as np

from recsys import synthetic
from recsys.evaluate import histories, run
from recsys.metrics import intra_list_diversity, ndcg_at_k
from recsys.models import ALS, Content, Hybrid, ItemKNN, Popularity, mmr
from recsys.split import relevant_items, temporal_user_split


def _setup():
    ds = synthetic.make(n_users=150, n_items=200, per_user=30)
    train, test = temporal_user_split(ds.ratings)
    return ds, train, relevant_items(test), histories(train)


def test_personalised_models_beat_popularity_on_planted_structure():
    ds, train, truth, hist = _setup()
    pop = Popularity().fit(train, ds.n_items)
    base = ndcg_at_k(run(pop, hist, truth), truth, 10)
    for model in (ALS(factors=16, iterations=8).fit(train, ds.n_items), ItemKNN().fit(train, ds.n_items)):
        assert ndcg_at_k(run(model, hist, truth), truth, 10) > base, model.name


def test_fold_in_matches_trained_user_factors():
    ds, train, _, hist = _setup()
    als = ALS(factors=8, iterations=10).fit(train, ds.n_items)
    items, ratings = hist[3]
    folded = als.user_vector(items, ratings)
    assert np.allclose(folded, als.user_factors[3], atol=1e-6)


def test_recommend_never_returns_history():
    ds, train, _, hist = _setup()
    content = Content().fit_text(ds.items.text.tolist())
    pop = Popularity().fit(train, ds.n_items)
    model = Hybrid(ALS(factors=8, iterations=4).fit(train, ds.n_items), ItemKNN().fit(train, ds.n_items), content, pop)
    items, ratings = hist[0]
    assert not set(model.recommend(items, ratings, 20)) & set(items.tolist())
    # an empty history (brand-new user) still gets a ranked list
    assert len(model.recommend(np.array([], np.int64), np.array([], np.float32), 10)) == 10


def test_mmr_keeps_best_first_and_increases_diversity():
    vectors = np.array([[1.0, 0.0]] * 5 + [[0.0, 1.0]] * 5)
    candidates = list(range(10))
    relevance = np.linspace(1.0, 0.5, 10)
    plain = candidates[:4]
    diverse = mmr(candidates, relevance, vectors, 4, lam=0.5)
    assert diverse[0] == 0
    assert intra_list_diversity({0: diverse}, vectors, 4) > intra_list_diversity({0: plain}, vectors, 4)
    assert mmr(candidates, relevance, vectors, 4, lam=1.0) == plain
