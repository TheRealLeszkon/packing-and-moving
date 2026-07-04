# syntax=docker/dockerfile:1
# Backend image for the API, worker, and migrations (all share this image; the
# compose file overrides the command per service).
FROM ghcr.io/astral-sh/uv:python3.13-bookworm-slim

# Runtime system deps: ffmpeg (video frame extraction + ffprobe duration checks)
# and libglib2.0-0 (required by opencv-python-headless).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PYTHONPATH=/app \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app

# Install dependencies first (cached layer) from the lockfile. --no-install-project
# keeps the app source out of this layer so code changes don't bust the dep cache;
# the app is importable via PYTHONPATH=/app instead of being pip-installed.
COPY pyproject.toml uv.lock README.md ./
RUN uv sync --frozen --no-install-project --no-dev \
    --extra workers --extra media --extra cloud

# Application code + migration assets.
COPY app ./app
COPY migrations ./migrations
COPY scripts ./scripts
COPY alembic.ini ./

EXPOSE 8000

# Default command runs the API; the worker/migrate services override this.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
