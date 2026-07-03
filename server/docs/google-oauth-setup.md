# Enabling Google Sign-In

The backend ships with a clean auth abstraction. Until you complete the Google
Cloud setup below, run with `AUTH_MODE=dev` (the default): the backend accepts
locally-minted **dev ID tokens** so the entire flow is testable without Google.
Switching to real Google verification is a **configuration change only** — no code
changes.

> **What "code-complete but not wired" means here:** `GoogleTokenVerifier`
> (`app/auth/verifiers.py`) is fully implemented against `google-auth`. It is not
> *active* only because `AUTH_MODE=dev`. Once the steps below are done, set
> `AUTH_MODE=google` + `GOOGLE_OAUTH_CLIENT_IDS` and it takes over.

---

## 1. OAuth consent screen

Google Cloud Console → **APIs & Services → OAuth consent screen**

- **User type:** External (or Internal if a Workspace-only app).
- **App name, support email, developer contact:** required.
- **Scopes:** `openid`, `email`, `profile` (no sensitive scopes needed — we only
  verify identity).
- **Publishing status:** while in *Testing*, add each tester's Google account
  under **Test users**, or you'll get `access_denied`. Publish to *Production*
  for public availability (may require verification if you later add sensitive
  scopes — we don't).

## 2. APIs to enable

APIs & Services → **Enabled APIs & services → + Enable APIs**

- No API strictly required for **ID-token verification** (it validates Google's
  public certs offline). Enable **"Google People API"** only if you later fetch
  extended profile data. For plain Sign-In, none is mandatory.

## 3. Credentials (OAuth client IDs)

APIs & Services → **Credentials → + Create credentials → OAuth client ID**

Create **two** clients:

1. **Android**
   - Application type: **Android**
   - Package name: your app's applicationId (e.g. `com.packingandmoving.surveyor`)
   - SHA-1 certificate fingerprint: from your signing keystore
     (`keytool -list -v -keystore <keystore> -alias <alias>`; use both debug and
     release fingerprints).
2. **Web** (the *server* client the Android app requests an ID token for)
   - Application type: **Web application**
   - This client's ID is the audience (`aud`) of the ID tokens your app should
     request, and what the backend validates against.

> **Redirect URIs:** not needed. This is native Sign-In returning an ID token to
> the app, not the OAuth authorization-code web flow. Leave redirect URIs empty.

## 4. Android app configuration

- Use Credential Manager / Google Identity Services **Sign in with Google**.
- Request an **ID token** using the **Web client ID** from step 3 as the
  "server client ID" (`setServerClientId(WEB_CLIENT_ID)`).
- Send that ID token to `POST /auth/google` as `{ "id_token": "<token>" }`.

## 5. Backend environment variables

In `server/.env`:

```bash
AUTH_MODE=google
# Comma-separated. Include the Web client ID; add the Android client ID too if
# you ever accept tokens minted directly for it.
GOOGLE_OAUTH_CLIENT_IDS=<WEB_CLIENT_ID>.apps.googleusercontent.com

# Unchanged, but ensure these are strong/rotated for production:
JWT_SECRET=<64+ random chars>
```

The backend validates: signature (Google certs), issuer (`accounts.google.com`),
expiry, `aud ∈ GOOGLE_OAUTH_CLIENT_IDS`, and `email_verified == true`.

## 6. Nothing else in the backend

No code changes. `build_token_verifier()` returns `GoogleTokenVerifier` as soon as
`AUTH_MODE=google`. `Settings` refuses to boot if `AUTH_MODE=google` without any
client IDs, or if `AUTH_MODE=dev` under `APP_ENV=production` — so a misconfigured
deploy fails fast instead of silently accepting dev tokens.

---

## Local testing without Google (dev mode)

```python
from app.auth.verifiers import mint_dev_id_token
from app.core.config import settings

token = mint_dev_id_token(
    subject="google-sub-123", email="you@example.com",
    name="Dev User", secret=settings.jwt_secret,
)
# POST /auth/google  { "id_token": token }  ->  backend JWT access + refresh
```
