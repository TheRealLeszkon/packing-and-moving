"""Top-level API router.

Aggregates every feature router into a single ``api_router`` mounted by the app
factory. New feature routers (auth, surveys, media, …) are included here as they
land in later phases, keeping ``main.py`` free of route wiring.
"""

from __future__ import annotations

from fastapi import APIRouter

from app.api.routes import auth, health, survey_requests, surveys, users

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(auth.router)
api_router.include_router(users.router)
api_router.include_router(surveys.router)
api_router.include_router(survey_requests.router)
