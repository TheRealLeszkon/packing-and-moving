"""Rate-limiting middleware.

Applies a per-client sliding-window limit, with a tighter budget for the ``/auth``
endpoints (credential-stuffing / token-minting abuse) than for the general API.
Health probes are never limited. On rejection it returns the standard error
envelope with HTTP 429 and a ``Retry-After`` header; successful responses carry
``X-RateLimit-*`` headers.

The limiter and limits are injected so the middleware is easy to exercise in tests
with a low threshold and a fake clock.
"""

from __future__ import annotations

from collections.abc import Callable

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from app.core.ratelimit import SlidingWindowLimiter
from app.core.responses import error_body

_AUTH_PREFIX = "/auth"
_EXEMPT_PREFIXES = ("/health", "/docs", "/redoc", "/openapi")


class RateLimitMiddleware(BaseHTTPMiddleware):
    def __init__(
        self,
        app,
        *,
        limiter: SlidingWindowLimiter,
        limit: int,
        auth_limit: int,
        window: float,
        clock: Callable[[], float] | None = None,
    ) -> None:
        super().__init__(app)
        self._limiter = limiter
        self._limit = limit
        self._auth_limit = auth_limit
        self._window = window

    def _client_key(self, request: Request) -> str:
        forwarded = request.headers.get("x-forwarded-for")
        if forwarded:
            return forwarded.split(",")[0].strip()
        return request.client.host if request.client else "unknown"

    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        path = request.url.path
        if path.startswith(_EXEMPT_PREFIXES):
            return await call_next(request)

        is_auth = path.startswith(_AUTH_PREFIX)
        limit = self._auth_limit if is_auth else self._limit
        scope = "auth" if is_auth else "api"
        key = f"{scope}:{self._client_key(request)}"

        result = self._limiter.check(key, limit=limit, window=self._window)
        if not result.allowed:
            response: Response = JSONResponse(
                status_code=429,
                content=error_body(
                    "rate_limited",
                    "Too many requests. Please slow down.",
                    {"retry_after": result.retry_after},
                ),
            )
            response.headers["Retry-After"] = str(result.retry_after)
        else:
            response = await call_next(request)

        response.headers["X-RateLimit-Limit"] = str(result.limit)
        response.headers["X-RateLimit-Remaining"] = str(result.remaining)
        return response
