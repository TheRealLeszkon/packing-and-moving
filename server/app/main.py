"""FastAPI application factory.

Wires together configuration, logging, middleware, exception handlers and the API
router. Keeping construction in a factory (rather than module-level side effects)
makes the app trivial to build with overridden settings in tests.

Run locally with:  uvicorn app.main:app --reload
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.router import api_router
from app.core.config import settings
from app.core.exceptions import register_exception_handlers
from app.core.logging import configure_logging
from app.db.session import dispose_engine
from app.middleware.request_context import RequestContextMiddleware

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    configure_logging(level=settings.log_level, json_output=settings.log_json)
    logger.info("application_startup", extra={"environment": settings.app_env})
    try:
        yield
    finally:
        await dispose_engine()
        logger.info("application_shutdown")


def create_app() -> FastAPI:
    app = FastAPI(
        title="Packing & Moving API",
        version="0.1.0",
        # Hide interactive docs in production; keep them for dev/staging.
        docs_url=None if settings.is_production else "/docs",
        redoc_url=None if settings.is_production else "/redoc",
        lifespan=lifespan,
    )

    app.add_middleware(RequestContextMiddleware)
    register_exception_handlers(app)
    app.include_router(api_router)
    return app


app = create_app()
