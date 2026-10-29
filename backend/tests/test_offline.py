import pickle

from app.schemas import Interaction
from app.service import OfflineModel
from recsys import synthetic
from recsys.train import DEFAULT_PARAMS, build_bundle


def test_offline_model_round_trip_and_tmdb_mapping(tmp_path):
    ds = synthetic.make(n_users=80, n_items=120, per_user=25)
    params = {"als": {**DEFAULT_PARAMS["als"], "factors": 8}, "hybrid": DEFAULT_PARAMS["hybrid"]}
    path = tmp_path / "model.pkl"
    path.write_bytes(pickle.dumps(build_bundle(ds, params)))
    model = OfflineModel(path)

    liked = [Interaction(id=10_000 + i, media_type="movie", signal="like") for i in (1, 2, 3)]
    tv_only = [Interaction(id=10_001, media_type="tv", signal="like")]
    recs = model.recommend(liked, 10)
    assert len(recs) == 10
    assert all(10_000 <= tid < 10_120 for tid, _ in recs)
    assert not {tid for tid, _ in recs} & {10_001, 10_002, 10_003}
    assert [s for _, s in recs] == sorted((s for _, s in recs), reverse=True)
    assert model.recommend(tv_only, 10) == []  # TV ids are not MovieLens movies
