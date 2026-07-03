"""Canonical API response envelopes.

Every response the API returns — success or failure — is wrapped in a consistent
shape so clients can rely on a single contract:

    { "success": true,  "data": ... }
    { "success": false, "error": { "code", "message", "details" } }
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ErrorDetail(BaseModel):
    code: str
    message: str
    details: dict[str, Any] = Field(default_factory=dict)


class SuccessResponse[T](BaseModel):
    success: bool = True
    data: T


class ErrorResponse(BaseModel):
    success: bool = False
    error: ErrorDetail


def error_body(
    code: str, message: str, details: dict[str, Any] | None = None
) -> dict[str, Any]:
    """Build a plain-dict error envelope for use in JSONResponse handlers."""
    error = ErrorDetail(code=code, message=message, details=details or {})
    return ErrorResponse(error=error).model_dump()
