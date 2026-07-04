# Packing & Moving — Backend

Production backend for the Packing & Moving Survey Platform. FastAPI (async) +
SQLAlchemy 2.0 + PostgreSQL, with a Dramatiq/Redis media & AI processing
pipeline. See [CLAUDE.md](CLAUDE.md) for the full specification.

## Quick start

```bash
# 1. Start backing services (Postgres + Redis)
docker compose up -d

# 2. Configure environment
cp .env.example .env      # then fill in secrets

# 3. Install dependencies
uv sync

# 4. Apply migrations (Phase 1+)
uv run alembic upgrade head

# 5. Run the API
uv run uvicorn app.main:app --reload

# 6. Run the background worker (media/AI processing) — needs Redis + extras
uv sync --extra workers --extra media --extra cloud
uv run dramatiq app.workers.actors
```

The API records a processing job and dispatches it to Redis on commit; the
Dramatiq worker consumes it (resize, frame extraction, blur/duplicate filtering).
When the last media of a survey finishes, the worker queues an **AI analysis**
run: Gemini analyses the processed images/frames, the detected inventory is saved
as `survey_items` (linked to their evidencing media), and only then does the
survey advance to `ready_for_review`. Set `PROCESSING_DISPATCH_ENABLED=false` to
record jobs without dispatching (e.g. when running the API without a worker), and
`AI_PROVIDER=stub` to run the pipeline without calling Gemini.

Health check: `GET http://localhost:8000/health` · Docs: `/docs` (non-prod).

Requests pass through hardening middleware: security headers, a per-client
sliding-window rate limiter (tighter on `/auth`), and in-process request metrics
(exposed at `GET /admin/metrics`, ADMIN only). Admin operators also get fleet-wide
listings and a failed-job requeue under `/admin`.

## Tests

```bash
uv sync --extra dev --extra workers --extra media --extra cloud
uv run pytest
```

Integration tests run the real ASGI app against the configured Postgres database
with in-memory storage and a deterministic stub AI provider (no Gemini calls, no
broker); they use unique per-test data, so no teardown is needed.

## Architecture

Clean layering — routes → services → repositories → database. See `app/`:

| Package        | Responsibility                                        |
| -------------- | ----------------------------------------------------- |
| `api/`         | Thin HTTP routes; validation + delegation only        |
| `core/`        | Config, logging, exceptions, response envelopes       |
| `db/`          | Declarative base, async engine/session                |
| `models/`      | SQLAlchemy 2.0 ORM models                             |
| `schemas/`     | Pydantic request/response DTOs                        |
| `services/`    | Business logic and orchestration                     |
| `repositories/`| Encapsulated database access                         |
| `auth/`        | Google token verification + JWT                      |
| `storage/`     | Google Cloud Storage abstraction                     |
| `ai/`          | Gemini provider behind a swappable interface         |
| `processing/`  | Image/video processing primitives                    |
| `workers/`     | Dramatiq actors and the pipeline                     |
| `dependencies/`| Shared FastAPI dependencies                          |
