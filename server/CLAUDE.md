# Claude Instructions

You are assisting with the backend development of an AI-assisted packing and moving survey system.

## Project Goal

Users upload images of household or office goods.

The backend stores the images in Google Cloud Storage, analyzes them with Gemini Flash, extracts structured metadata, stores the metadata in PostgreSQL, and exposes REST endpoints for retrieval.

The project is intended to be easy to understand and maintain.

---

## Coding Style

Keep the code:

- Simple
- Readable
- Explicit
- Modular
- Beginner-friendly

Avoid unnecessary abstractions.

Prefer straightforward code over clever code.

Avoid overengineering.

---

## Architecture

Organize code into:

```
app/
    api/
    services/
    database/
    models/
    schemas/
    prompts/
    config.py
```

Business logic belongs in services.

Routes should only:

- validate input
- call services
- return responses

---

## Database

Use SQLModel.

Prefer one model per file.

Use PostgreSQL.

---

## AI

Use the official Google GenAI SDK.

Store prompts in a dedicated module.

Use structured JSON responses.

Validate all Gemini output before storing.

---

## Cloud Storage

Use Google Cloud Storage.

Store only the Cloud Storage URI in the database.

Never store image binaries in PostgreSQL.

---

## Configuration

Use environment variables.

Never hardcode:

- API keys
- database credentials
- bucket names
- project IDs

Load configuration using Pydantic Settings.

---

## Error Handling

Fail gracefully.

Return meaningful HTTP errors.

Log failures.

Avoid broad except blocks.

---

## General Guidelines

Write production-quality code without unnecessary complexity.

Prefer small functions.

Keep files reasonably short.

Comment only when necessary.

Choose clarity over cleverness.
