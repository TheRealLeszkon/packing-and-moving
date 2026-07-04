"""In-process sliding-window rate limiter.

A small, dependency-free limiter keyed by an arbitrary string (e.g. client IP +
route class). It keeps per-key request timestamps and admits a request only if
fewer than ``limit`` fell within the trailing ``window`` seconds.

Scope: this is per-process. A multi-instance deployment should back the same
``allow`` contract with Redis (e.g. a sorted-set window); callers depend only on
the method, so swapping the implementation is localised.
"""

from __future__ import annotations

import threading
import time
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class RateLimitResult:
    allowed: bool
    limit: int
    remaining: int
    retry_after: int  # seconds until the next request would be admitted (0 if allowed)


class SlidingWindowLimiter:
    def __init__(self, *, clock: Callable[[], float] = time.monotonic) -> None:
        self._clock = clock
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def check(self, key: str, *, limit: int, window: float) -> RateLimitResult:
        """Record an attempt for ``key`` and report whether it is admitted."""
        now = self._clock()
        cutoff = now - window
        with self._lock:
            bucket = self._hits.get(key)
            if bucket is None:
                bucket = deque()
                self._hits[key] = bucket
            while bucket and bucket[0] <= cutoff:
                bucket.popleft()
            if len(bucket) >= limit:
                retry_after = max(1, int(bucket[0] + window - now) + 1)
                return RateLimitResult(False, limit, 0, retry_after)
            bucket.append(now)
            return RateLimitResult(True, limit, limit - len(bucket), 0)

    def reset(self, key: str | None = None) -> None:
        with self._lock:
            if key is None:
                self._hits.clear()
            else:
                self._hits.pop(key, None)
