"""Request-context middleware.

Assigns every request a correlation id, binds it (plus method/path) to the
logging context, measures execution time, and emits a single structured access
log line on completion. The request id is echoed back in the ``X-Request-ID``
header so clients and logs can be cross-referenced. User id is bound later by
the auth dependency once the caller is identified.
"""

from __future__ import annotations

import logging
import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from app.core.logging import bind_request_context, clear_request_context

logger = logging.getLogger("app.access")

_REQUEST_ID_HEADER = "X-Request-ID"


class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(
        self, request: Request, call_next: RequestResponseEndpoint
    ) -> Response:
        request_id = request.headers.get(_REQUEST_ID_HEADER) or uuid.uuid4().hex
        request.state.request_id = request_id
        bind_request_context(
            request_id=request_id,
            method=request.method,
            path=request.url.path,
        )

        start = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception:
            # Global exception handlers produce the body; log timing + re-raise.
            duration_ms = round((time.perf_counter() - start) * 1000, 2)
            logger.exception("request_failed", extra={"duration_ms": duration_ms})
            raise
        else:
            duration_ms = round((time.perf_counter() - start) * 1000, 2)
            response.headers[_REQUEST_ID_HEADER] = request_id
            logger.info(
                "request_completed",
                extra={"status_code": response.status_code, "duration_ms": duration_ms},
            )
            return response
        finally:
            clear_request_context()
