from app.llm import parse_with_rules, sanitize
from app.schemas import QueryIntent


def test_rules_parse_reference_genre_decade():
    intent = parse_with_rules("a funny sci-fi show like Dark but lighter from the 90s")
    assert intent.media_type == "tv"
    assert intent.reference_titles == ["Dark"]
    assert {"comedy", "science fiction"} <= set(intent.genres)
    assert (intent.year_min, intent.year_max) == (1990, 1999)


def test_rules_parse_negation_and_acclaim():
    intent = parse_with_rules("acclaimed movies, no horror")
    assert intent.media_type == "movie"
    assert intent.exclude_genres == ["horror"]
    assert "horror" not in intent.genres
    assert intent.min_rating == 7.0


def test_sanitize_drops_what_the_system_cannot_use():
    raw = QueryIntent(genres=["Sci-Fi", "space opera", "comedy"], exclude_genres=["comedy"], year_min=1066,
                      year_max=2005, min_rating=42, language="english", reference_titles=[" ", "Dark"],
                      keywords=["a", "b", "c", "d", "e"])
    clean = sanitize(raw, "q")
    assert clean.genres == ["science fiction"]  # unknown dropped, excluded wins over included
    assert clean.year_min is None and clean.year_max == 2005
    assert clean.min_rating is None and clean.language is None
    assert clean.reference_titles == ["Dark"]
    assert len(clean.keywords) == 4
