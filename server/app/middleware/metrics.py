"""Metrics middleware — feeds the in-process :data:`metrics` registry.

Counts requests, tracks in-flight concurrency, records latency, and buckets
responses by status class. Registered inside the request-context middleware so
timing covers routing + handler work.
"""

from __future__ import annotations

import time

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from app.core.metrics import MetricsRegistry, metrics


class MetricsMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, *, registry: MetricsRegistry | None = None) -> None:
        super().__init__(app)
        self._metrics = registry or metrics

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        self._metrics.request_started()
        start = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            return response
        finally:
            duration_ms = (time.perf_counter() - start) * 1000
            self._metrics.request_finished(status_code=status_code, duration_ms=duration_ms)
