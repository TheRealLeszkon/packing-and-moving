"""Worker runtime helpers.

Actors are synchronous, but the storage abstraction is async. ``run_async`` runs a
storage coroutine to completion from sync worker code. Each call uses a short-lived
event loop, which is fine for the storage backends (GCS offloads to threads;
in-memory is instant) and keeps no async state between tasks.
"""

from __future__ import annotations

import asyncio
from collections.abc import Coroutine


def run_async[T](coro: Coroutine[object, object, T]) -> T:
    return asyncio.run(coro)
