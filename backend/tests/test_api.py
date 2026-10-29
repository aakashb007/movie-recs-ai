import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from tests.fake_tmdb import FakeServices


@pytest.fixture
def services():
    return FakeServices()


def client_for(services: FakeServices, monkeypatch, groq_key: str | None = None) -> TestClient:
    monkeypatch.setenv("TMDB_API_KEY", "test-key")
    monkeypatch.setenv("MODEL_PATH", "/nonexistent/model.pkl")
    if groq_key:
        monkeypatch.setenv("GROQ_API_KEY", groq_key)
    else:
        monkeypatch.delenv("GROQ_API_KEY", raising=False)
    return TestClient(create_app(transport=services.transport()))


def ids_of(items):
    return [(i["title"]["media_type"], i["title"]["id"]) for i in items]


def test_onboarding_spreads_across_genres(services, monkeypatch):
    with client_for(services, monkeypatch) as client:
        items = client.get("/v1/onboarding").json()["items"]
    assert len(items) >= 10
    first_ten_genres = {g for t in items[:10] for g in t["genres"]}
    assert len(first_ten_genres) >= 4


def test_cold_start_without_likes(services, monkeypatch):
    with client_for(services, monkeypatch) as client:
        body = client.post("/v1/recommendations", json={"interactions": []}).json()
    assert body["strategy"] == "cold_start"
    assert body["items"]


def test_personalised_recs_are_explained_by_real_likes_and_skip_rated(services, monkeypatch):
    likes = [{"id": 1, "media_type": "movie", "signal": "like"},  # theme comedy (1 % 5 == 1)
             {"id": 6, "media_type": "movie", "signal": "like"},
             {"id": 11, "media_type": "movie", "signal": "seen"},
             {"id": 16, "media_type": "movie", "signal": "dismiss"}]
    with client_for(services, monkeypatch) as client:
        body = client.post("/v1/recommendations", json={"interactions": likes, "limit": 10}).json()
    assert body["strategy"] == "personalized"
    got = ids_of(body["items"])
    assert got and not {("movie", 1), ("movie", 6), ("movie", 11), ("movie", 16)} & set(got)
    assert set(got) <= services.ids()
    for item in body["items"]:
        assert item["because"]["id"] in (1, 6)
        assert item["reason"]
    # the first results share the liked titles' genre
    assert body["items"][0]["title"]["genres"] == ["Comedy"]


def test_media_type_filter(services, monkeypatch):
    likes = [{"id": 102, "media_type": "tv", "signal": "like"}, {"id": 3, "media_type": "movie", "signal": "like"}]
    with client_for(services, monkeypatch) as client:
        body = client.post("/v1/recommendations", json={"interactions": likes, "media_type": "tv"}).json()
    assert body["items"] and all(t == "tv" for t, _ in ids_of(body["items"]))


def test_llm_cannot_inject_titles_that_do_not_exist(monkeypatch):
    services = FakeServices(groq_reply={
        "media_type": "movie",
        "genres": ["sci-fi", "not-a-genre"],
        "reference_titles": ["Zorblax 9: The Invented Sequel"],
        "keywords": ["time travel"],
        "year_min": 1990, "year_max": 2020,
        "summary": "sci-fi films",
        "results": [{"id": 999999, "title": "Hallucinated Movie"}],  # extra keys are ignored
    })
    with client_for(services, monkeypatch, groq_key="gsk_test") as client:
        body = client.post("/v1/ask", json={"query": "time travel sci-fi movies"}).json()
    assert body["parser"] == "llm"
    assert body["interpretation"]["genres"] == ["science fiction"]
    assert body["references"] == []  # the invented title found no TMDB match and was dropped
    got = ids_of(body["items"])
    assert got and set(got) <= services.ids()
    for item in body["items"]:
        t = item["title"]
        assert t["media_type"] == "movie" and 1990 <= t["year"] <= 2020
        assert "Science Fiction" in t["genres"]


def test_ask_falls_back_to_rules_when_llm_fails(monkeypatch):
    services = FakeServices(groq_status=500)
    with client_for(services, monkeypatch, groq_key="gsk_test") as client:
        body = client.post("/v1/ask", json={"query": "shows like Show 102, no comedy"}).json()
    assert body["parser"] == "rules"
    assert [r["title"] for r in body["references"]] == ["Show 102"]
    assert body["items"]
    for item in body["items"]:
        assert item["title"]["media_type"] == "tv"
        assert "Comedy" not in item["title"]["genres"]
        assert item["title"]["id"] != 102


def test_ask_respects_seen_and_language(services, monkeypatch):
    with client_for(services, monkeypatch) as client:
        body = client.post("/v1/ask", json={
            "query": "crime series", "interactions": [{"id": 103, "media_type": "tv", "signal": "seen"}],
        }).json()
    assert ("tv", 103) not in ids_of(body["items"])


def test_title_detail_uses_region(services, monkeypatch):
    with client_for(services, monkeypatch) as client:
        gb = client.get("/v1/titles/movie/7?region=GB").json()
        us = client.get("/v1/titles/movie/7?region=US").json()
        missing = client.get("/v1/titles/movie/424242")
    assert [p["name"] for p in gb["providers"]["stream"]] == ["StreamCo"]
    assert us["providers"]["stream"] == []
    assert gb["trailer_youtube_key"] == "abc123"
    assert missing.status_code == 404


def test_health_reports_capabilities(services, monkeypatch):
    with client_for(services, monkeypatch) as client:
        assert client.get("/health").json() == {"ok": True, "offline_model": False, "llm": False}
