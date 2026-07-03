"""Health and readiness endpoints.

- ``/health``  — liveness: the process is up. Cheap, no dependencies. Used by
  container/orchestrator liveness probes.
- ``/health/ready`` — readiness: the process can serve traffic, i.e. its
  critical dependencies (the database) are reachable. Used by load-balancer
  readiness probes and returns 503 when a dependency is down.
"""

from __future__ import annotations

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.db.session import get_session

router = APIRouter(tags=["health"])
logger = logging.getLogger(__name__)


@router.get("/health", summary="Liveness probe")
async def health(settings: Annotated[Settings, Depends(get_settings)]) -> dict[str, str]:
    return {"status": "ok", "environment": settings.app_env}


@router.get("/health/ready", summary="Readiness probe")
async def readiness(session: Annotated[AsyncSession, Depends(get_session)]) -> JSONResponse:
    checks: dict[str, str] = {}
    healthy = True

    try:
        await session.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except Exception as exc:  # noqa: BLE001 - readiness must report, not raise
        logger.warning("readiness_db_failed", extra={"error": str(exc)})
        checks["database"] = "unavailable"
        healthy = False

    return JSONResponse(
        status_code=status.HTTP_200_OK if healthy else status.HTTP_503_SERVICE_UNAVAILABLE,
        content={"status": "ready" if healthy else "not_ready", "checks": checks},
    )
