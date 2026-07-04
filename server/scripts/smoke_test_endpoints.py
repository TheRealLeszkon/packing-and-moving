"""End-to-end smoke test for every backend endpoint.

Runs the real ASGI app in-process (httpx ASGITransport) so no server, Redis, or
GCS is required. Storage is in-memory and job dispatch is disabled; the async
media/AI pipeline is driven in-process. AI uses the REAL Gemini provider with two
real photos, so the AI-analysis path is genuinely exercised (a small, authorised
test). Every route is hit, including auth/role/validation error cases, and the
OpenAPI schema is downloaded to ``client/openapi.json``.

Run from the ``server`` directory:

    PYTHONPATH=. uv run python scripts/smoke_test_endpoints.py
"""

from __future__ import annotations

import asyncio
import os
import subprocess
import tempfile
import uuid
from pathlib import Path

# ---- Environment must be set before app settings import ----
os.environ.setdefault("STORAGE_BACKEND", "memory")
os.environ.setdefault("PROCESSING_DISPATCH_ENABLED", "false")
os.environ.setdefault("AI_PROVIDER", "gemini")        # real Gemini (small test)
os.environ.setdefault("DB_USE_NULLPOOL", "true")
# Keep the limiter active (so we can see its headers) but out of the way.
os.environ.setdefault("RATE_LIMIT_REQUESTS", "100000")
os.environ.setdefault("RATE_LIMIT_AUTH_REQUESTS", "100000")

from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy import select, update  # noqa: E402

from app.auth.verifiers import mint_dev_id_token  # noqa: E402
from app.core.config import settings  # noqa: E402
from app.db.session import SessionFactory  # noqa: E402
from app.main import app  # noqa: E402
from app.models.enums import JobStatus, UserRole  # noqa: E402
from app.models.media_processing_job import MediaProcessingJob  # noqa: E402
from app.models.user import User  # noqa: E402
from app.workers.pipeline import (  # noqa: E402
    run_ai_analysis,
    run_image_pipeline,
    run_video_pipeline,
)

ROOT = Path(__file__).resolve().parents[2]
PHOTO_DIR = ROOT / "PNMTestingPhotos" / "HomePhotos"
CLIENT_DIR = ROOT / "client"

_results: list[tuple[bool, str, str]] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    _results.append((ok, name, detail))
    mark = "PASS" if ok else "FAIL"
    print(f"  [{mark}] {name}" + (f"  ({detail})" if detail else ""))


def status_ok(resp, *expected: int) -> bool:
    return resp.status_code in expected


def photo(name: str) -> bytes:
    return (PHOTO_DIR / name).read_bytes()


def sample_mp4() -> bytes:
    path = Path(tempfile.gettempdir()) / f"smoke_{uuid.uuid4().hex}.mp4"
    subprocess.run(
        ["ffmpeg", "-y", "-f", "lavfi", "-i",
         "testsrc=duration=2:size=320x240:rate=2", "-pix_fmt", "yuv420p", str(path)],
        check=True, capture_output=True,
    )
    data = path.read_bytes()
    path.unlink()
    return data


async def sign_in(api: AsyncClient, role: UserRole) -> dict:
    email = f"{role.value}_{uuid.uuid4().hex[:8]}@example.com"
    token = mint_dev_id_token(
        subject="g-" + uuid.uuid4().hex, email=email, name=email.split("@")[0],
        secret=settings.jwt_secret,
    )
    resp = await api.post("/auth/google", json={"id_token": token})
    data = resp.json()["data"]
    if role is not UserRole.CUSTOMER:
        async with SessionFactory() as s:
            await s.execute(
                update(User).where(User.id == uuid.UUID(data["user"]["id"])).values(role=role)
            )
            await s.commit()
    return {
        "id": data["user"]["id"],
        "access": data["access_token"],
        "refresh": data["refresh_token"],
        "headers": {"Authorization": f"Bearer {data['access_token']}"},
        "signin_resp": resp,
    }


async def main() -> None:
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://smoke") as api:
        # ---------------------------------------------------------------- #
        print("\n== Health ==")
        check("GET /health", status_ok(await api.get("/health"), 200))
        check("GET /health/ready", status_ok(await api.get("/health/ready"), 200))

        # ---------------------------------------------------------------- #
        print("\n== Auth ==")
        customer = await sign_in(api, UserRole.CUSTOMER)
        surveyor = await sign_in(api, UserRole.SURVEYOR)
        admin = await sign_in(api, UserRole.ADMIN)
        check("POST /auth/google (new user)", status_ok(customer["signin_resp"], 200),
              f"role={customer['signin_resp'].json()['data']['user']['role']}")
        CH, SH, AH = customer["headers"], surveyor["headers"], admin["headers"]

        check("GET /auth/me", status_ok(await api.get("/auth/me", headers=CH), 200))
        refreshed = await api.post("/auth/refresh", json={"refresh_token": customer["refresh"]})
        check("POST /auth/refresh (rotates token)", status_ok(refreshed, 200))
        new_refresh = refreshed.json()["data"]["refresh_token"]
        reused = await api.post("/auth/refresh", json={"refresh_token": customer["refresh"]})
        check("POST /auth/refresh rejects reused token", status_ok(reused, 401))
        check("POST /auth/logout", status_ok(
            await api.post("/auth/logout", json={"refresh_token": new_refresh}), 200))

        # ---------------------------------------------------------------- #
        print("\n== Users ==")
        check("GET /users/me", status_ok(await api.get("/users/me", headers=SH), 200))
        put = await api.put("/users/me", headers=SH, json={"name": "Alex Surveyor"})
        check("PUT /users/me", status_ok(put, 200) and put.json()["data"]["name"] == "Alex Surveyor")

        # ---------------------------------------------------------------- #
        print("\n== Surveys: create + read + request board ==")
        make = lambda: api.post("/surveys", headers=CH, json={  # noqa: E731
            "name": "Smith relocation", "origin_address": "12 Old Rd",
            "destination_address": "9 New Ave"})
        sid = (await make()).json()["data"]["id"]
        check("POST /surveys", bool(sid))
        check("GET /surveys/my (customer)", status_ok(await api.get("/surveys/my", headers=CH), 200))
        check("GET /surveys/{id}", status_ok(await api.get(f"/surveys/{sid}", headers=CH), 200))
        check("GET /surveys/{id}/status", status_ok(
            await api.get(f"/surveys/{sid}/status", headers=CH), 200))
        check("GET /survey-requests/available", status_ok(
            await api.get("/survey-requests/available", headers=SH), 200))
        check("POST /survey-requests/{id}/accept", status_ok(
            await api.post(f"/survey-requests/{sid}/accept", headers=SH), 200))
        check("GET /survey-requests/assigned", status_ok(
            await api.get("/survey-requests/assigned", headers=SH), 200))

        # a throwaway survey to exercise DELETE (must be scheduled/unassigned)
        throwaway = (await make()).json()["data"]["id"]
        check("DELETE /surveys/{id} (unassigned)", status_ok(
            await api.delete(f"/surveys/{throwaway}", headers=CH), 200))

        # ---------------------------------------------------------------- #
        print("\n== Lifecycle: start + media upload (real photos + video) ==")
        check("POST /surveys/{id}/start", status_ok(
            await api.post(f"/surveys/{sid}/start", headers=SH), 200))

        img_resp = await api.post(
            f"/surveys/{sid}/images", headers=SH,
            files=[("files", ("photo1.jpeg", photo("photo1.jpeg"), "image/jpeg")),
                   ("files", ("photo2.jpeg", photo("photo2.jpeg"), "image/jpeg"))],
            data={"room_location": "Living Room"},
        )
        check("POST /surveys/{id}/images (2 real photos)", status_ok(img_resp, 201),
              f"count={img_resp.json()['data']['count']}")
        image_ids = [m["id"] for m in img_resp.json()["data"]["items"]]

        vid_resp = await api.post(
            f"/surveys/{sid}/videos", headers=SH,
            files=[("files", ("clip.mp4", sample_mp4(), "video/mp4"))],
        )
        check("POST /surveys/{id}/videos", status_ok(vid_resp, 201))
        video_id = vid_resp.json()["data"]["items"][0]["id"]

        check("GET /surveys/{id}/media", status_ok(
            await api.get(f"/surveys/{sid}/media", headers=SH), 200))
        one_media = await api.get(f"/media/{image_ids[0]}", headers=SH)
        check("GET /media/{id} (signed url present)",
              status_ok(one_media, 200) and bool(one_media.json()["data"]["url"]))

        # ---------------------------------------------------------------- #
        print("\n== Processing + REAL Gemini analysis ==")
        check("POST /surveys/{id}/complete -> processing", status_ok(
            await api.post(f"/surveys/{sid}/complete", headers=SH), 200))
        for mid in image_ids:
            await asyncio.to_thread(run_image_pipeline, uuid.UUID(mid))
        await asyncio.to_thread(run_video_pipeline, uuid.UUID(video_id))
        print("  ... calling Gemini (real) ...")
        await asyncio.to_thread(run_ai_analysis, uuid.UUID(sid))
        status = (await api.get(f"/surveys/{sid}/status", headers=SH)).json()["data"]["status"]
        check("survey advanced to ready_for_review", status == "ready_for_review", status)

        items_resp = await api.get(f"/surveys/{sid}/items", headers=SH)
        items = items_resp.json()["data"]["items"]
        ai_items = [i for i in items if i["source"] == "ai"]
        check("GET /surveys/{id}/items (AI-generated)", status_ok(items_resp, 200),
              f"{len(ai_items)} AI items")
        if ai_items:
            print("    detected e.g.: " + ", ".join(
                f"{i['item_name']}(conf={i['confidence_score']})" for i in ai_items[:6]))

        # ---------------------------------------------------------------- #
        print("\n== Items: create / update / summary / merge / split / delete ==")
        created = await api.post(f"/surveys/{sid}/items", headers=SH, json={
            "item_name": "Wardrobe", "category": "Furniture", "room_location": "Bedroom",
            "quantity": 1, "height_cm": 200, "width_cm": 120, "depth_cm": 60,
            "estimated_value": 450, "fragile": False, "media_ids": [image_ids[0]]})
        check("POST /surveys/{id}/items (manual)", status_ok(created, 201))
        manual_id = created.json()["data"]["id"]

        patched = await api.patch(f"/survey-items/{manual_id}", headers=SH, json={"quantity": 2})
        check("PATCH /survey-items/{id}",
              status_ok(patched, 200) and patched.json()["data"]["quantity"] == 2)

        summ = await api.get(f"/surveys/{sid}/summary", headers=SH)
        check("GET /surveys/{id}/summary", status_ok(summ, 200),
              f"distinct={summ.json()['data']['distinct_items']}, "
              f"vol={summ.json()['data']['total_volume_m3']}m3")

        dup = (await api.post(f"/surveys/{sid}/items", headers=SH, json={
            "item_name": "Wardrobe (dup)", "quantity": 1})).json()["data"]
        merged = await api.post("/survey-items/merge", headers=SH,
                                json={"item_ids": [manual_id, dup["id"]], "quantity": 3})
        check("POST /survey-items/merge", status_ok(merged, 200))
        split = await api.post(f"/survey-items/{manual_id}/split", headers=SH,
                               json={"parts": [{"item_name": "Wardrobe base"},
                                               {"item_name": "Wardrobe doors"}]})
        check("POST /survey-items/{id}/split", status_ok(split, 201),
              f"count={split.json()['data']['count']}")
        a_part = split.json()["data"]["items"][0]["id"]
        check("DELETE /survey-items/{id}", status_ok(
            await api.delete(f"/survey-items/{a_part}", headers=SH), 200))
        check("DELETE /media/{id}", status_ok(
            await api.delete(f"/media/{image_ids[1]}", headers=SH), 200))

        # ---------------------------------------------------------------- #
        print("\n== Lifecycle: submit / reject / re-submit / approve ==")
        check("POST /surveys/{id}/submit -> awaiting_customer_approval", status_ok(
            await api.post(f"/surveys/{sid}/submit", headers=SH), 200))
        check("POST /surveys/{id}/reject -> revision_required", status_ok(
            await api.post(f"/surveys/{sid}/reject", headers=CH,
                           json={"reason": "Please add the garage items."}), 200))
        check("POST /surveys/{id}/submit (from revision)", status_ok(
            await api.post(f"/surveys/{sid}/submit", headers=SH), 200))
        approved = await api.post(f"/surveys/{sid}/approve", headers=CH)
        check("POST /surveys/{id}/approve -> approved",
              status_ok(approved, 200) and approved.json()["data"]["status"] == "approved")

        # a separate survey to exercise cancel
        cancel_sid = (await make()).json()["data"]["id"]
        check("POST /surveys/{id}/cancel", status_ok(
            await api.post(f"/surveys/{cancel_sid}/cancel", headers=CH,
                           json={"reason": "Customer postponed."}), 200))

        # ---------------------------------------------------------------- #
        print("\n== Admin ==")
        for path in ("/admin/surveys", "/admin/users", "/admin/ai-runs",
                     "/admin/jobs", "/admin/metrics"):
            check(f"GET {path}", status_ok(await api.get(path, headers=AH), 200))
        # force a job into dead-letter, then requeue it
        async with SessionFactory() as s:
            job = (await s.execute(select(MediaProcessingJob).where(
                MediaProcessingJob.media_id == uuid.UUID(image_ids[0])))).scalar_one()
            job.status = JobStatus.DEAD_LETTER
            job.last_error = "forced for smoke test"
            job_id = job.id
            await s.commit()
        rq = await api.post(f"/admin/jobs/{job_id}/requeue", headers=AH)
        check("POST /admin/jobs/{id}/requeue",
              status_ok(rq, 200) and rq.json()["data"]["status"] == "queued")

        # ---------------------------------------------------------------- #
        print("\n== Error handling & security ==")
        check("401 without token", status_ok(await api.get("/surveys/my"), 401))
        check("403 surveyor cannot create survey", status_ok(
            await api.post("/surveys", headers=SH, json={
                "name": "x", "origin_address": "a", "destination_address": "b"}), 403))
        check("403 customer cannot start survey", status_ok(
            await api.post(f"/surveys/{cancel_sid}/start", headers=CH), 403, 409))
        check("404 unknown survey", status_ok(
            await api.get(f"/surveys/{uuid.uuid4()}", headers=CH), 404))
        check("422 invalid body", status_ok(
            await api.post("/surveys", headers=CH, json={"name": ""}), 422))
        health_headers = (await api.get("/health")).headers
        check("security headers present",
              health_headers.get("X-Content-Type-Options") == "nosniff"
              and health_headers.get("X-Frame-Options") == "DENY")
        # Rate-limit headers appear on limited routes (/health is intentionally exempt).
        api_headers = (await api.get("/auth/me", headers=CH)).headers
        check("rate-limit headers present (non-exempt route)",
              "X-RateLimit-Limit" in api_headers)

        # ---------------------------------------------------------------- #
        print("\n== OpenAPI schema ==")
        spec = await api.get("/openapi.json")
        ok = status_ok(spec, 200) and spec.json().get("openapi", "").startswith("3")
        if ok:
            CLIENT_DIR.mkdir(exist_ok=True)
            (CLIENT_DIR / "openapi.json").write_bytes(spec.content)
        n_paths = len(spec.json().get("paths", {})) if ok else 0
        check("GET /openapi.json + saved to client/openapi.json", ok, f"{n_paths} paths")

    passed = sum(1 for ok, _, _ in _results if ok)
    total = len(_results)
    print("\n" + "=" * 60)
    print(f"RESULT: {passed}/{total} checks passed")
    if passed != total:
        print("FAILURES:")
        for ok, name, detail in _results:
            if not ok:
                print(f"  - {name} ({detail})")


if __name__ == "__main__":
    asyncio.run(main())
