"""HTTP API for the Android app. Holds the TMDB and Groq keys so they never ship in the APK."""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi import Path as PathParam

from . import llm
from .schemas import (
    AskRequest,
    AskResponse,
    MediaType,
    OnboardingResponse,
    RecommendationRequest,
    RecommendationResponse,
    TitleDetail,
)
from .service import OfflineModel, Recommender
from .tmdb import TMDB, TMDBError

log = logging.getLogger("movie_recs")


def create_app(transport: httpx.AsyncBaseTransport | None = None) -> FastAPI:
    """transport lets tests swap in a fake TMDB/Groq without touching the network."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        tmdb_key = os.environ.get("TMDB_API_KEY", "")
        if not tmdb_key:
            log.warning("TMDB_API_KEY is not set; every TMDB call will fail")
        model_path = Path(os.environ.get("MODEL_PATH", "artifacts/model.pkl"))
        offline = OfflineModel(model_path) if model_path.exists() else None
        if offline is None:
            log.warning("no offline model at %s; serving TMDB + content signals only", model_path)
        async with httpx.AsyncClient(transport=transport) as client:
            app.state.client = client
            app.state.recommender = Recommender(TMDB(tmdb_key, client), offline)
            app.state.groq_key = os.environ.get("GROQ_API_KEY") or None
            app.state.groq_model = os.environ.get("GROQ_MODEL", "llama-3.3-70b-versatile")
            app.state.offline_loaded = offline is not None
            yield

    app = FastAPI(title="Movie Recs API", version="1.0.0", lifespan=lifespan)

    @app.get("/health")
    async def health():
        return {"ok": True, "offline_model": app.state.offline_loaded, "llm": bool(app.state.groq_key)}

    @app.get("/v1/onboarding", response_model=OnboardingResponse)
    async def onboarding():
        try:
            return OnboardingResponse(items=await app.state.recommender.onboarding())
        except TMDBError as exc:
            raise HTTPException(502, str(exc)) from exc

    @app.post("/v1/recommendations", response_model=RecommendationResponse)
    async def recommendations(req: RecommendationRequest):
        try:
            items, strategy = await app.state.recommender.recommend(req.interactions, req.media_type, req.limit)
        except TMDBError as exc:
            raise HTTPException(502, str(exc)) from exc
        return RecommendationResponse(items=items, strategy=strategy)

    @app.post("/v1/ask", response_model=AskResponse)
    async def ask(req: AskRequest):
        intent, parser = await llm.parse(req.query, app.state.groq_key, app.state.groq_model, app.state.client)
        try:
            items, refs = await app.state.recommender.ask(intent, req.query, req.interactions, req.limit)
        except TMDBError as exc:
            raise HTTPException(502, str(exc)) from exc
        return AskResponse(interpretation=intent, references=refs, items=items, parser=parser)

    @app.get("/v1/titles/{media_type}/{tmdb_id}", response_model=TitleDetail)
    async def title(
        media_type: MediaType,
        tmdb_id: int = PathParam(gt=0),
        region: str = Query("US", min_length=2, max_length=2),
    ):
        try:
            return await app.state.recommender.tmdb.details(media_type, tmdb_id, region)
        except TMDBError as exc:
            raise HTTPException(404, str(exc)) from exc

    return app


app = create_app()
