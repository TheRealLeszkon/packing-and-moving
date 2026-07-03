"""Shared, reusable schemas."""

from __future__ import annotations

from pydantic import BaseModel


class MessageResponse(BaseModel):
    """A simple human-readable acknowledgement payload."""

    detail: str
