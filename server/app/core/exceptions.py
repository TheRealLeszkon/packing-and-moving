"""Application exception hierarchy and global handlers.

Services and repositories raise these typed exceptions; routes never build error
responses by hand. ``register_exception_handlers`` maps them (plus FastAPI's
validation errors and any uncaught exception) onto the ``{success, error}``
envelope, ensuring stack traces are logged but never leaked to clients.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.responses import error_body

logger = logging.getLogger(__name__)


class AppError(Exception):
    """Base class for expected, client-facing errors.

    ``code`` is a stable machine-readable string; ``status_code`` the HTTP status;
    ``details`` optional structured context (never sensitive data).
    """

    code: str = "internal_error"
    status_code: int = status.HTTP_500_INTERNAL_SERVER_ERROR
    message: str = "An unexpected error occurred."

    def __init__(
        self,
        message: str | None = None,
        *,
        details: dict[str, Any] | None = None,
    ) -> None:
        self.message = message or self.message
        self.details = details or {}
        super().__init__(self.message)


class NotFoundError(AppError):
    code = "not_found"
    status_code = status.HTTP_404_NOT_FOUND
    message = "Resource not found."


class ValidationError(AppError):
    code = "validation_error"
    status_code = status.HTTP_422_UNPROCESSABLE_CONTENT
    message = "The request failed validation."


class AuthenticationError(AppError):
    code = "unauthenticated"
    status_code = status.HTTP_401_UNAUTHORIZED
    message = "Authentication required."


class AuthorizationError(AppError):
    code = "forbidden"
    status_code = status.HTTP_403_FORBIDDEN
    message = "You do not have permission to perform this action."


class ConflictError(AppError):
    code = "conflict"
    status_code = status.HTTP_409_CONFLICT
    message = "The request conflicts with the current state of the resource."


class InvalidStateTransitionError(ConflictError):
    code = "invalid_state_transition"
    message = "This action is not allowed in the resource's current state."


class ExternalServiceError(AppError):
    code = "external_service_error"
    status_code = status.HTTP_502_BAD_GATEWAY
    message = "An upstream service failed."


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def _handle_app_error(_: Request, exc: AppError) -> JSONResponse:
        # Expected errors: log at info/warning, no stack trace.
        logger.info("app_error", extra={"error_code": exc.code, "detail": exc.message})
        return JSONResponse(
            status_code=exc.status_code,
            content=error_body(exc.code, exc.message, exc.details),
        )

    @app.exception_handler(RequestValidationError)
    async def _handle_validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content=error_body(
                "validation_error",
                "The request failed validation.",
                {"errors": exc.errors()},
            ),
        )

    @app.exception_handler(StarletteHTTPException)
    async def _handle_http(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=error_body("http_error", str(exc.detail)),
        )

    @app.exception_handler(Exception)
    async def _handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
        # Unexpected: log the full trace, return an opaque message.
        logger.exception("unhandled_exception", extra={"error_type": type(exc).__name__})
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=error_body("internal_error", "An unexpected error occurred."),
        )
