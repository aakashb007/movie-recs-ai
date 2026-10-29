"""Wire format shared with the Android app (snake_case JSON)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

MediaType = Literal["movie", "tv"]
Signal = Literal["like", "dislike", "seen", "dismiss"]


class TitleRef(BaseModel):
    id: int
    media_type: MediaType
    title: str


class TitleSummary(BaseModel):
    id: int
    media_type: MediaType
    title: str
    year: int | None = None
    overview: str = ""
    poster_path: str | None = None
    backdrop_path: str | None = None
    vote_average: float = 0.0
    vote_count: int = 0
    popularity: float = 0.0
    genres: list[str] = Field(default_factory=list)
    original_language: str | None = None

    def key(self) -> tuple[str, int]:
        return (self.media_type, self.id)


class Interaction(BaseModel):
    id: int
    media_type: MediaType
    signal: Signal


class RecommendationRequest(BaseModel):
    interactions: list[Interaction] = Field(default_factory=list, max_length=500)
    media_type: Literal["all", "movie", "tv"] = "all"
    region: str = Field("US", min_length=2, max_length=2)
    limit: int = Field(30, ge=1, le=60)


class RecItem(BaseModel):
    title: TitleSummary
    score: float
    reason: str
    because: TitleRef | None = None


class RecommendationResponse(BaseModel):
    items: list[RecItem]
    strategy: str


class AskRequest(BaseModel):
    query: str = Field(min_length=2, max_length=300)
    interactions: list[Interaction] = Field(default_factory=list, max_length=500)
    region: str = Field("US", min_length=2, max_length=2)
    limit: int = Field(20, ge=1, le=40)


class QueryIntent(BaseModel):
    """What the LLM is allowed to say about a request. It never names result titles or ids."""

    media_type: Literal["movie", "tv", "any"] = "any"
    genres: list[str] = Field(default_factory=list)
    exclude_genres: list[str] = Field(default_factory=list)
    keywords: list[str] = Field(default_factory=list)
    reference_titles: list[str] = Field(default_factory=list)
    year_min: int | None = None
    year_max: int | None = None
    min_rating: float | None = None
    language: str | None = None
    summary: str = ""


class AskResponse(BaseModel):
    interpretation: QueryIntent
    references: list[TitleRef]
    items: list[RecItem]
    parser: Literal["llm", "rules"]


class Provider(BaseModel):
    name: str
    logo_path: str | None = None


class Providers(BaseModel):
    link: str | None = None
    stream: list[Provider] = Field(default_factory=list)
    free: list[Provider] = Field(default_factory=list)
    rent: list[Provider] = Field(default_factory=list)
    buy: list[Provider] = Field(default_factory=list)


class CastMember(BaseModel):
    name: str
    character: str = ""
    profile_path: str | None = None


class TitleDetail(BaseModel):
    summary: TitleSummary
    tagline: str = ""
    runtime: int | None = None
    seasons: int | None = None
    status: str = ""
    cast: list[CastMember] = Field(default_factory=list)
    trailer_youtube_key: str | None = None
    providers: Providers
    similar: list[TitleSummary] = Field(default_factory=list)


class OnboardingResponse(BaseModel):
    items: list[TitleSummary]
