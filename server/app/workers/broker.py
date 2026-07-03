"""Dramatiq broker configuration.

Importing this module configures the process-wide Redis broker. The worker is
started with ``dramatiq app.workers.actors`` (which imports this), and the API
imports it lazily only when it actually dispatches a job.

Retries/backoff are provided by Dramatiq's default middleware; final-failure
("dead-letter") semantics live in our ``media_processing_jobs`` ledger rather
than a Redis DLQ, so failures are queryable and recoverable from the database.
"""

from __future__ import annotations

import dramatiq
from dramatiq.brokers.redis import RedisBroker

from app.core.config import settings

broker = RedisBroker(url=settings.redis_url)
dramatiq.set_broker(broker)
