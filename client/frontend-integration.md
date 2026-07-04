# Backend Integration Brief — Packing & Moving API

Context for building the SurveyAgent Android client against the existing backend.
The backend is complete (8 phases). Treat the API as the source of truth.

---

## 1. Stack & how to run
- FastAPI (async) + PostgreSQL + SQLAlchemy 2, Dramatiq/Redis worker, GCS storage, Gemini AI. Python 3.13, `uv`.
- Local API: `uv run uvicorn app.main:app --reload` → `http://localhost:8000`. Worker: `uv run dramatiq app.workers.actors`.
- Interactive docs (non-prod): `GET /docs`, OpenAPI at `/openapi.json` — **the client can generate models / Retrofit interfaces from this.**
- No API version prefix (routes are at root, e.g. `/surveys`). No trailing-slash redirects assumed.

## 2. Global conventions (apply to every endpoint)
**Success envelope:**
```json
{ "success": true, "data": <payload> }
```
**Error envelope:**
```json
{ "success": false, "error": { "code": "string", "message": "string", "details": {} } }
```
**Error codes → HTTP status** (stable `code` strings; branch on these, not on message):

| code | HTTP | when |
|---|---|---|
| `unauthenticated` | 401 | missing/invalid/expired access token |
| `forbidden` | 403 | wrong role or not owner/assignee |
| `not_found` | 404 | missing or not-visible-to-you (existence hidden) |
| `conflict` | 409 | state conflict / optimistic-lock retry |
| `invalid_state_transition` | 409 | action not allowed in current survey status |
| `validation_error` | 422 | body/query validation (`details.errors` from FastAPI) |
| `rate_limited` | 429 | see rate limiting; `details.retry_after` seconds |
| `external_service_error` | 502 | upstream (GCS/Gemini) failure |
| `internal_error` | 500 | unexpected |

**Auth header:** `Authorization: Bearer <access_token>` on everything except `/health*`, `/auth/google`, `/auth/refresh`, `/docs`.
**Headers to know:** every response echoes `X-Request-ID` (log it for support). `X-RateLimit-Limit` / `X-RateLimit-Remaining` on all; `Retry-After` on 429. Security headers set (`nosniff`, `DENY`, CSP).
**Pagination:** list endpoints take `?limit=` (default 20, max 100) & `?offset=` (default 0). Response `data` shape: `{ "items": [...], "total": int, "limit": int, "offset": int }`.
**CORS is NOT configured** (fine for native Android; a web client would need it added).

## 3. Auth flow & token lifecycle
1. Android does Google Sign-In → obtains a **Google ID token**.
2. `POST /auth/google` `{ "id_token": "..." }` → `data`:
```json
{ "access_token": "...", "refresh_token": "...", "token_type": "bearer",
  "expires_in": 1800, "user": { "id","email","name","role","is_active","created_at" } }
```
3. Use `access_token` (JWT) as bearer. **Access TTL = 30 min**, **refresh TTL = 30 days**.
4. `POST /auth/refresh` `{ "refresh_token": "..." }` → new token pair. **Refresh tokens rotate & are single-use** — always store the newly returned one; a reused/old refresh token is rejected.
5. `POST /auth/logout` `{ "refresh_token": "..." }` revokes it. `GET /auth/me` → current user.
- **Dev/local mode:** backend currently runs `AUTH_MODE=dev` (real Google OAuth console setup deferred). In dev it accepts a locally-minted token, so the app can be developed without Google wiring. The client flow is identical; only the token source differs. Flag needed from backend when real OAuth is enabled: the **allowed Google OAuth client IDs / Android client ID**.

## 4. Roles
`customer`, `surveyor`, `admin`. A user has exactly one role (defaults to `customer` on first sign-in; surveyor/admin are assigned server-side). Branch UI on `user.role`.
- **customer:** create/view own surveys, approve/reject, cancel.
- **surveyor:** browse open requests, accept, upload media, edit inventory, submit. **(The SurveyAgent client is a surveyor app.)**
- **admin:** `/admin/*` tooling.

## 5. Survey lifecycle (the backbone of the UX)
States (`status` string values):
`scheduled → assigned → in_progress → processing → ready_for_review → awaiting_customer_approval → {approved | revision_required} …`, plus terminal `completed`, `cancelled`.

Transitions and who triggers them:

| action (endpoint) | from → to | actor |
|---|---|---|
| `POST /survey-requests/{id}/accept` | scheduled → assigned | any surveyor |
| `POST /surveys/{id}/start` | assigned / revision_required → in_progress | assigned surveyor |
| `POST /surveys/{id}/complete` | in_progress → processing | assigned surveyor |
| *(automatic)* | processing → ready_for_review | system (pipeline + AI) |
| `POST /surveys/{id}/submit` | ready_for_review / revision_required → awaiting_customer_approval | assigned surveyor |
| `POST /surveys/{id}/approve` | awaiting_customer_approval → approved | customer owner |
| `POST /surveys/{id}/reject` `{reason}` | awaiting_customer_approval → revision_required | customer owner |
| `POST /surveys/{id}/cancel` `{reason?}` | most non-terminal → cancelled | customer owner |

**Drive the UI off the server, don't hardcode transitions:** `GET /surveys/{id}/status` returns `{ id, status, available_actions: [...] }` — render exactly the buttons in `available_actions`.

## 6. Async processing model (important for screens)
After `complete`, the survey sits in **`processing`** while the worker resizes images, extracts video frames, and runs Gemini. The client **polls** `GET /surveys/{id}/status` (or the survey) until it flips to `ready_for_review`. There is **no push/websocket** — build a polling "Processing…" screen. AI failures still advance to `ready_for_review` (with an empty/partial inventory) so it never gets stuck.

## 7. Endpoint reference
**Auth:** `POST /auth/google`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`
**Users:** `GET /users/me`, `PUT /users/me` `{name}`
**Surveys (customer/surveyor):**
- `POST /surveys` (customer) `{name, origin_address, destination_address, preferred_datetime?}`
- `GET /surveys/my` (paginated — owned or assigned)
- `GET /surveys/{id}`, `GET /surveys/{id}/status`, `DELETE /surveys/{id}` (only scheduled/unassigned)
- Transitions: `start`, `complete`, `submit`, `approve`, `reject`, `cancel` (see §5)
- `GET /surveys/{id}/items`, `POST /surveys/{id}/items` (surveyor add), `GET /surveys/{id}/summary`

**Request board (surveyor):** `GET /survey-requests/available`, `GET /survey-requests/assigned`, `POST /survey-requests/{id}/accept`
**Media:** `POST /surveys/{id}/images`, `POST /surveys/{id}/videos` (multipart), `GET /surveys/{id}/media` (paginated), `GET /media/{id}`, `DELETE /media/{id}`
**Items:** `PATCH /survey-items/{id}`, `DELETE /survey-items/{id}`, `POST /survey-items/merge`, `POST /survey-items/{id}/split`
**Admin:** `GET /admin/{surveys,users,ai-runs,jobs,metrics}`, `POST /admin/jobs/{id}/requeue`
**Health:** `GET /health`, `GET /health/ready`

## 8. Core object shapes
**Survey:** `id, name, origin_address, destination_address, customer_id, surveyor_id?, preferred_datetime?, status, total_volume_estimate?, total_value_estimate?, created_at, updated_at`
**Media:** `id, survey_id, media_type, processing_status, room_location?, width?, height?, duration_seconds?, frame_number?, parent_video_id?, upload_timestamp, url?` — `url` is a **short-lived signed GCS URL** present on `GET /media/{id}` and list responses (not on the upload ack).
**SurveyItem:** `id, survey_id, item_name, category?, quantity, room_location?, height_cm?, width_cm?, depth_cm?, weight_kg?, material?, fragile, needs_disassembly, needs_special_handling, needs_to_ship, packing_difficulty?, lifting_difficulty?, estimated_value?, condition?, remarks?, confidence_score?, source, created_at, updated_at, media_ids: [uuid]` — `source` is `"ai"` or `"manual"`; `media_ids` are the evidencing photos (fetch each via `GET /media/{id}` for a signed URL). `confidence_score` (0–1) drives the "high/low confidence" badge in the AI Report screen.
**Summary** (`GET /surveys/{id}/summary`): `survey_id, distinct_items, total_quantity, total_volume_m3, total_value, fragile_items, needs_disassembly_items, needs_special_handling_items, by_category:[{category,distinct_items,quantity}], by_room:[{room_location,distinct_items,quantity}]`.

## 9. Enum values (exact strings)
- **SurveyStatus:** `scheduled, assigned, in_progress, processing, ready_for_review, awaiting_customer_approval, revision_required, approved, completed, cancelled`
- **MediaType:** `image, video, processed_image, extracted_frame`
- **ProcessingStatus:** `pending, processing, completed, failed, skipped`
- **Difficulty:** `easy, medium, hard`
- **ItemCondition:** `new, good, fair, poor, damaged`
- **UserRole:** `customer, surveyor, admin`
- (admin-only) **JobStatus:** `queued, running, succeeded, failed, dead_letter`; **AIRunStatus:** `pending, succeeded, failed`

## 10. Media upload specifics
- Multipart form: field **`files`** (repeatable — batch upload) + optional **`room_location`** form field.
- **Images:** JPEG/PNG, ≤ 25 MB each. **Videos:** MP4, ≤ 200 MB, **≤ 30 s** (rejected otherwise with 422).
- Upload only allowed for the **assigned surveyor** while survey is **`in_progress`**.
- Response is the created `Media` rows with `processing_status: "pending"` (no `url` yet). The client then polls media/survey status; processed images/frames get `url` once `completed`.
- Editing inventory (`POST /surveys/{id}/items`, `PATCH/DELETE /survey-items/*`, merge/split) is only allowed for the **assigned surveyor** while status is **`ready_for_review`** or **`revision_required`** (else 403/409). `media_ids` in item bodies must belong to the same survey (else 422).

## 11. Image display notes
Signed URLs expire (**~15 min**, `SIGNED_URL_TTL_SECONDS=900`). Don't cache them long-term; re-fetch `GET /media/{id}` when showing an image after a while. Good fit for Coil with a short memory cache keyed by media id, refreshing the URL on demand.

## 12. What's NOT built yet (plan around these)
- **No push notifications / websockets / SSE** → client must poll for `processing → ready_for_review` and for customer-approval state changes.
- **No survey-history endpoint** exposed (audit rows exist server-side, not surfaced).
- **Real Google OAuth not wired** (dev auth mode now) — needs the Android OAuth client ID before production.
- **No CORS** (only matters for a web client).
- Rate limits are per-instance in-memory (fine for one API instance).

## 13. Suggested screen ↔ endpoint map (aligns with DESIGN.md)
- **Sign-in** → `POST /auth/google`; store token pair; `GET /auth/me` on launch.
- **Home / dashboard (3.1)** → `GET /surveys/my` (assigned surveys); request board → `GET /survey-requests/available|assigned`.
- **Create Survey (3.2)** → `POST /surveys` (note: server fields are `name`, `origin_address`, `destination_address`, `preferred_datetime?` — the design's Customer Name/Phone/Email/Notes fields don't all have backend columns yet; map `name`→customer/survey name, `origin_address`→Pickup, `destination_address`→Destination, and flag the extra fields as backend gaps).
- **Survey detail** → `GET /surveys/{id}` + `GET /surveys/{id}/status` (render action buttons from `available_actions`).
- **Camera / capture (3.3)** → `POST …/images` / `…/videos`; a **Processing** screen polls `…/status`.
- **AI Survey Report (3.4)** → `GET …/items` (source=`ai`; use `confidence_score` for the check/warning badge; item image via first `media_ids` → `GET /media/{id}`). Add-item FAB → `POST …/items`.
- **Item Detail (3.5)** → `PATCH /survey-items/{id}`; gallery from `media_ids`; "95% Match" = `confidence_score`.
- **Final Summary (3.6)** → `GET …/summary` (stat grid = total volume/value/items; special-requirements chips from `fragile_items` / `needs_disassembly_items` / `needs_special_handling_items`); "Complete Survey" → `submit`.
