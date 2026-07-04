"""In-process request metrics.

A tiny thread-safe registry of counters/gauges plus a latency accumulator, good
enough for a single-instance deployment or as a scrape source behind an admin
endpoint. For a fleet, export these to Prometheus/OTel instead; the snapshot shape
is intentionally flat and JSON-serialisable.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass, field
from typing import Any


@dataclass
class _State:
    total: int = 0
    in_flight: int = 0
    errors: int = 0                       # responses with status >= 500
    by_status_class: dict[str, int] = field(default_factory=dict)  # "2xx", "4xx", ...
    latency_ms_sum: float = 0.0


class MetricsRegistry:
    def __init__(self) -> None:
        self._state = _State()
        self._lock = threading.Lock()

    def request_started(self) -> None:
        with self._lock:
            self._state.in_flight += 1

    def request_finished(self, *, status_code: int, duration_ms: float) -> None:
        bucket = f"{status_code // 100}xx"
        with self._lock:
            s = self._state
            s.in_flight = max(0, s.in_flight - 1)
            s.total += 1
            s.latency_ms_sum += duration_ms
            s.by_status_class[bucket] = s.by_status_class.get(bucket, 0) + 1
            if status_code >= 500:
                s.errors += 1

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            s = self._state
            avg = s.latency_ms_sum / s.total if s.total else 0.0
            return {
                "requests_total": s.total,
                "requests_in_flight": s.in_flight,
                "errors_total": s.errors,
                "by_status_class": dict(sorted(s.by_status_class.items())),
                "avg_latency_ms": round(avg, 2),
            }

    def reset(self) -> None:
        with self._lock:
            self._state = _State()


# Process-wide registry shared by the middleware and the admin metrics endpoint.
metrics = MetricsRegistry()
