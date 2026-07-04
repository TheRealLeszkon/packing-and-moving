"""End-to-end verification of the real Google OAuth sign-in path.

Performs a genuine Google sign-in via the loopback (Desktop-app) flow to obtain a
*real* Google-issued ID token, then feeds it to the running application's
``POST /auth/google`` in-process (AUTH_MODE=google) and prints the resulting user
and role. This is the positive counterpart to the automated negative-path checks.

Requires a human to complete sign-in in the browser window that opens.

Run from ``server/``::

    uv run --with google-auth-oauthlib python scripts/verify_google_oauth.py

Notes:
- Uses the *web* OAuth client secret JSON (the one with a client_secret). The
  Android client cannot be used for a browser/loopback flow. The web client's
  client_id is one of the audiences the backend accepts, so this fully exercises
  the backend verification path.
- ONE-TIME SETUP: add ``http://localhost:8765/`` to the web OAuth client's
  "Authorized redirect URIs" in the Google Cloud console (web clients require a
  pre-registered redirect URI; the fixed port below matches it).
- No secrets are printed. The ID token is not written to disk.
"""

from __future__ import annotations

import asyncio
import glob
import json
import os
import sys

# Make ``app`` importable no matter the current working directory: the server
# root is this file's grandparent (server/scripts/verify_google_oauth.py).
_SERVER_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _SERVER_ROOT not in sys.path:
    sys.path.insert(0, _SERVER_ROOT)

# Do not override the developer's .env (AUTH_MODE=google). Only neutralise
# external I/O that boot would otherwise attempt.
os.environ.setdefault("STORAGE_BACKEND", "memory")
os.environ.setdefault("PROCESSING_DISPATCH_ENABLED", "false")
os.environ.setdefault("RATE_LIMIT_ENABLED", "false")
os.environ.setdefault("DB_USE_NULLPOOL", "true")

SCOPES = [
    "openid",
    "https://www.googleapis.com/auth/userinfo.email",
    "https://www.googleapis.com/auth/userinfo.profile",
]

# Web clients require an exact pre-registered redirect URI, so we pin the port.
# Register http://localhost:8765/ on the web OAuth client before running.
LOOPBACK_PORT = 8765


def _find_web_client_secret() -> str:
    """Return the web OAuth client secret JSON (the one carrying a client_secret)."""
    for path in glob.glob(os.path.join(_SERVER_ROOT, "client_secret_*.json")):
        data = json.load(open(path))
        node = data.get("web") or data.get("installed") or {}
        if node.get("client_secret"):
            return path
    print(
        "ERROR: no web client_secret_*.json (with a client_secret) found in server/.\n"
        "The Android client cannot do a browser/loopback flow; a web client is required.",
        file=sys.stderr,
    )
    raise SystemExit(2)


def obtain_real_id_token() -> str:
    from google_auth_oauthlib.flow import InstalledAppFlow

    secret_file = _find_web_client_secret()
    flow = InstalledAppFlow.from_client_secrets_file(secret_file, scopes=SCOPES)
    print("A browser window will open for Google sign-in. Complete it to continue…")
    print(
        f"If you see redirect_uri_mismatch, add http://localhost:{LOOPBACK_PORT}/ to the "
        "web OAuth client's Authorized redirect URIs.\n"
    )
    flow.run_local_server(port=LOOPBACK_PORT, open_browser=True, prompt="consent")
    id_token = flow.credentials.id_token
    if not id_token:
        print("ERROR: no id_token returned (was 'openid' scope granted?)", file=sys.stderr)
        raise SystemExit(1)
    return id_token


async def verify_against_app(id_token: str) -> None:
    import jwt  # PyJWT, already a dependency
    from httpx import ASGITransport, AsyncClient

    from app.core.config import settings
    from app.main import app

    claims = jwt.decode(id_token, options={"verify_signature": False})
    print("Real Google ID token obtained:")
    print("  aud  :", claims.get("aud"))
    print("  email:", claims.get("email"), "| verified:", claims.get("email_verified"))
    print("  iss  :", claims.get("iss"))
    print("  aud accepted by backend:", claims.get("aud") in settings.allowed_google_client_ids)
    print("  auth_mode:", settings.auth_mode.value)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://verify") as client:
        resp = await client.post("/auth/google", json={"id_token": id_token})
        print("\nPOST /auth/google ->", resp.status_code)
        body = resp.json()
        if not body.get("success"):
            print("  FAILED:", json.dumps(body.get("error"), indent=2))
            raise SystemExit(1)
        data = body["data"]
        user = data["user"]
        print("  success! user created/logged in:")
        print("    id   :", user["id"])
        print("    email:", user["email"])
        print("    name :", user.get("name"))
        print("    role :", user["role"])
        print("    access_token present:", bool(data.get("access_token")))
        print("    refresh_token present:", bool(data.get("refresh_token")))

        # Exercise the issued access token on a protected route.
        me = await client.get(
            "/auth/me", headers={"Authorization": f"Bearer {data['access_token']}"}
        )
        print("\nGET /auth/me with issued token ->", me.status_code)
        if me.status_code == 200:
            print("    /auth/me role:", me.json()["data"]["role"])
    print("\n✅ Real Google OAuth sign-in verified end-to-end.")


def main() -> None:
    token = obtain_real_id_token()
    asyncio.run(verify_against_app(token))


if __name__ == "__main__":
    main()
