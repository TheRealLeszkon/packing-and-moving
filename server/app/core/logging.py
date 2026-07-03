"""Structured logging.

Uses the standard library ``logging`` (per project convention) with a JSON
formatter for production and a concise human formatter for local development.

A ``ContextVar`` carries per-request fields (request id, user id, method, path)
so that any log emitted while handling a request is automatically correlated,
without threading a logger through every function. The request-context
middleware populates and clears it.
"""

from __future__ import annotations

import json
import logging
import sys
from contextvars import ContextVar
from typing import Any

# Fields injected into every log record emitted during a request.
# Default is None (not a mutable {}) so the default is never shared/mutated.
_request_context: ContextVar[dict[str, Any] | None] = ContextVar(
    "request_context", default=None
)

# Standard LogRecord attributes we never want to duplicate into the JSON payload.
_RESERVED = set(logging.makeLogRecord({}).__dict__) | {"message", "asctime", "taskName"}


def bind_request_context(**fields: Any) -> None:
    """Merge ``fields`` into the current request's logging context."""
    _request_context.set({**get_request_context(), **fields})


def clear_request_context() -> None:
    _request_context.set(None)


def get_request_context() -> dict[str, Any]:
    return _request_context.get() or {}


class _ContextFilter(logging.Filter):
    """Attach the current request context onto every record."""

    def filter(self, record: logging.LogRecord) -> bool:
        for key, value in get_request_context().items():
            setattr(record, key, value)
        return True


class JsonFormatter(logging.Formatter):
    """Render records as single-line JSON for log aggregators."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        # Extra (non-reserved) attributes = request context + explicit `extra=`.
        for key, value in record.__dict__.items():
            if key not in _RESERVED and not key.startswith("_"):
                payload[key] = value
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str, ensure_ascii=False)


class HumanFormatter(logging.Formatter):
    """Readable formatter for local development."""

    _FMT = "%(asctime)s %(levelname)-7s %(name)s %(message)s"

    def __init__(self) -> None:
        super().__init__(self._FMT, datefmt="%H:%M:%S")

    def format(self, record: logging.LogRecord) -> str:
        base = super().format(record)
        ctx = {k: getattr(record, k) for k in ("request_id", "user_id") if hasattr(record, k)}
        return f"{base}  {ctx}" if ctx else base


def configure_logging(level: str = "INFO", json_output: bool = True) -> None:
    """Install the root handler. Idempotent; safe to call on startup."""
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter() if json_output else HumanFormatter())
    handler.addFilter(_ContextFilter())

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())

    # Uvicorn owns its own access log; route its loggers through ours and mute
    # the duplicate access logger (our middleware emits a richer access line).
    for name in ("uvicorn", "uvicorn.error"):
        logging.getLogger(name).handlers.clear()
    logging.getLogger("uvicorn.access").disabled = True
