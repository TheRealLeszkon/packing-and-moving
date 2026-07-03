"""Top-level API router.

Aggregates every feature router into a single ``api_router`` mounted by the app
factory. New feature routers (auth, surveys, media, …) are included here as they
land in later phases, keeping ``main.py`` free of route wiring.
"""

from __future__ import annotations

from fastapi import APIRouter

from app.api.routes import health

api_router = APIRouter()
api_router.include_router(health.router)
