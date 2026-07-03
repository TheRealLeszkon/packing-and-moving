# CLAUDE.md

## Project

Packing & Moving Backend

This project is a production-oriented backend for a Packing and Moving application that allows customers to request home inventory surveys and surveyors to capture images and videos of household items. AI is used to analyze uploaded media and generate inventory estimates.

---

# Tech Stack

Backend

- Python 3.13+
- FastAPI
- SQLAlchemy 2.x
- Alembic
- PostgreSQL

Authentication

- Google OAuth
- JWT Authentication

Storage

- Google Cloud Storage

AI

- Gemini Flash 2.5

Image Processing

- Pillow
- OpenCV

Video Processing

- FFmpeg

Background Processing

- Celery or Dramatiq (choose one)
- Redis (if required)

Package Manager

- uv

---

# Project Goals

The backend should be designed for production use.

Avoid writing prototype code.

Code should be:

- modular
- maintainable
- strongly typed
- testable
- asynchronous where appropriate

---

# Architecture

Follow Clean Architecture.

```
app/

    api/

    core/

    db/

    models/

    schemas/

    services/

    repositories/

    workers/

    processing/

    storage/

    auth/

    ai/

    dependencies/

    utils/
```

Business logic must never live inside route handlers.

Routes should only:

- validate requests
- call services
- return responses

---

# Coding Style

Use:

- type hints everywhere
- SQLAlchemy 2.x syntax
- Pydantic v2
- async FastAPI endpoints
- dependency injection

Avoid:

- global variables
- duplicated logic
- giant route files

Keep functions small.

Prefer composition over inheritance.

---

# Authentication

Users authenticate using Google Sign-In.

Android obtains an ID Token.

Backend:

1. Verify Google ID Token
2. Find or create user
3. Generate JWT
4. Return JWT

Every authenticated endpoint should use the backend JWT.

Never trust data supplied directly by the client.

---

# User Roles

CUSTOMER

Can:

- request surveys
- view surveys
- approve surveys
- reject surveys

SURVEYOR

Can:

- browse available surveys
- accept surveys
- upload media
- edit inventory
- submit surveys

Enforce role-based authorization.

---

# Survey Workflow

Survey statuses

```
SCHEDULED

ASSIGNED

IN_PROGRESS

PROCESSING

READY_FOR_REVIEW

AWAITING_CUSTOMER_APPROVAL

REVISION_REQUIRED

APPROVED

COMPLETED

CANCELLED
```

Only allow valid state transitions.

---

# Database

Tables

- users
- surveys
- media
- survey_items
- item_media

Always use SQLAlchemy ORM.

Never write raw SQL unless necessary.

Use Alembic migrations.

---

# Media Pipeline

Images

Upload

↓

Google Cloud Storage

↓

Resize to 1280 × 960

↓

Gemini

↓

Survey Items

Videos

Upload

↓

Google Cloud Storage

↓

Extract Frames (1 FPS)

↓

Remove blurry frames

↓

Remove duplicate frames (optional)

↓

Resize

↓

Gemini

↓

Survey Items

Heavy processing must never happen inside request handlers.

Always queue background jobs.

---

# Google Cloud Storage

Original uploads and processed media should both be stored.

Do not store binary files in PostgreSQL.

Store only URLs and metadata.

---

# Gemini

Gemini receives processed media.

Gemini returns structured JSON.

Never trust Gemini output directly.

Always:

- validate JSON
- validate data types
- handle missing fields
- gracefully handle API failures

---

# Error Handling

Return consistent error responses.

Use FastAPI exception handlers.

Do not expose stack traces.

Log all unexpected exceptions.

---

# Logging

Use Python logging.

Every request should include:

- request id
- user id
- endpoint
- response status
- execution time

Background workers should also log:

- processing duration
- AI latency
- upload latency
- failures

---

# API Design

REST only.

Plural resource names.

Examples

GET /surveys

POST /surveys

GET /surveys/{id}

PATCH /survey-items/{id}

DELETE /survey-items/{id}

Return proper HTTP status codes.

---

# Validation

Validate:

- UUIDs
- image formats
- video formats
- file size
- JWT
- user roles

Reject invalid input early.

---

# Security

Never trust client input.

Always validate ownership.

Surveyors cannot edit another surveyor's surveys.

Customers cannot modify another customer's surveys.

Never expose secrets.

Read secrets from environment variables.

---

# Performance

Use async endpoints.

Stream uploads.

Avoid loading large files entirely into memory.

Use pagination where appropriate.

Batch database writes whenever possible.

---

# File Upload Limits

Images

- JPEG
- PNG

Videos

- MP4

Maximum video length

30 seconds

Maximum upload size should be configurable.

---

# Background Jobs

Long-running work must execute in workers.

Examples

- image resize
- video frame extraction
- blur detection
- duplicate detection
- Gemini requests
- thumbnail generation

API endpoints should return immediately after queueing work.

---

# AI Output

Gemini should return structured JSON only.

Validate the response using Pydantic models before saving.

Do not allow malformed AI responses into the database.

---

# Code Generation Rules

When generating code:

- follow existing architecture
- prefer reusable services
- keep routers thin
- write docstrings
- use dependency injection
- avoid duplicated code
- write production-quality implementations

If additional libraries are required, explain why before introducing them.

Do not generate placeholder code unless explicitly requested.

Always prefer maintainability over brevity.
