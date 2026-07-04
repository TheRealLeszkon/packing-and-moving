"""Unit tests for Google OAuth token verification wiring.

These exercise the config → verifier selection and the ``GoogleTokenVerifier``
guards without any network I/O: ``google-auth``'s ``verify_oauth2_token`` is
monkeypatched to return canned claims, so we test *our* audience/email logic.
"""

from __future__ import annotations

import sys
import types

import pytest

from app.auth.verifiers import (
    DevTokenVerifier,
    GoogleTokenVerifier,
    build_token_verifier,
)
from app.core.config import AppEnv, AuthMode, Settings
from app.core.exceptions import AuthenticationError

_BASE_ENV = {
    "db_url": "postgresql://localhost:5432/test",
    "jwt_secret": "x" * 40,
    "gcp_project_id": "test-project",
    "gcs_bucket_name": "test-bucket",
}


def _settings(**overrides: object) -> Settings:
    # ``_env_file=None`` isolates the test from the developer's real ``.env`` (which
    # may set AUTH_MODE / client IDs), so these assert on the explicit inputs only.
    return Settings(_env_file=None, **{**_BASE_ENV, **overrides})  # type: ignore[arg-type]


def test_merges_individual_client_ids() -> None:
    cfg = _settings(mobile_client_id="mob.apps", web_client_id="web.apps")
    assert cfg.allowed_google_client_ids == ["mob.apps", "web.apps"]


def test_merges_and_dedupes_all_sources() -> None:
    cfg = _settings(
        google_oauth_client_ids="web.apps, extra.apps",
        mobile_client_id="mob.apps",
        web_client_id="web.apps",  # duplicate of the comma-separated one
    )
    assert cfg.allowed_google_client_ids == ["web.apps", "extra.apps", "mob.apps"]


def test_google_mode_requires_a_client_id() -> None:
    with pytest.raises(ValueError, match="requires GOOGLE_OAUTH_CLIENT_IDS"):
        _settings(auth_mode=AuthMode.GOOGLE)


def test_production_forbids_dev_mode() -> None:
    with pytest.raises(ValueError, match="auth_mode=dev is not permitted"):
        _settings(app_env=AppEnv.PRODUCTION, auth_mode=AuthMode.DEV, gemini_api_key="k")


def test_factory_selects_google_verifier() -> None:
    cfg = _settings(auth_mode=AuthMode.GOOGLE, mobile_client_id="mob.apps")
    assert isinstance(build_token_verifier(cfg), GoogleTokenVerifier)


def test_factory_selects_dev_verifier_by_default() -> None:
    assert isinstance(build_token_verifier(_settings()), DevTokenVerifier)


def _patch_google_claims(monkeypatch: pytest.MonkeyPatch, claims: dict[str, object]) -> None:
    """Install fake ``google.oauth2.id_token`` / ``google.auth.transport`` modules
    so ``GoogleTokenVerifier._verify_sync`` returns ``claims`` without network."""
    transport = types.ModuleType("google.auth.transport")
    requests_mod = types.ModuleType("google.auth.transport.requests")
    requests_mod.Request = lambda: object()  # type: ignore[attr-defined]
    oauth2 = types.ModuleType("google.oauth2")
    id_token_mod = types.ModuleType("google.oauth2.id_token")
    id_token_mod.verify_oauth2_token = (  # type: ignore[attr-defined]
        lambda token, request, audience=None: claims
    )
    for name, mod in {
        "google.auth.transport": transport,
        "google.auth.transport.requests": requests_mod,
        "google.oauth2": oauth2,
        "google.oauth2.id_token": id_token_mod,
    }.items():
        monkeypatch.setitem(sys.modules, name, mod)


async def test_google_verifier_accepts_valid_token(monkeypatch: pytest.MonkeyPatch) -> None:
    _patch_google_claims(
        monkeypatch,
        {
            "sub": "g-123",
            "email": "a@example.com",
            "email_verified": True,
            "name": "Ada",
            "aud": "mob.apps",
        },
    )
    identity = await GoogleTokenVerifier(["mob.apps"]).verify("tok")
    assert identity.subject == "g-123"
    assert identity.email == "a@example.com"
    assert identity.name == "Ada"


async def test_google_verifier_rejects_wrong_audience(monkeypatch: pytest.MonkeyPatch) -> None:
    _patch_google_claims(
        monkeypatch,
        {"sub": "g-1", "email": "a@example.com", "email_verified": True, "aud": "other.apps"},
    )
    with pytest.raises(AuthenticationError, match="audience"):
        await GoogleTokenVerifier(["mob.apps"]).verify("tok")


async def test_google_verifier_rejects_unverified_email(monkeypatch: pytest.MonkeyPatch) -> None:
    _patch_google_claims(
        monkeypatch,
        {"sub": "g-1", "email": "a@example.com", "email_verified": False, "aud": "mob.apps"},
    )
    with pytest.raises(AuthenticationError, match="not verified"):
        await GoogleTokenVerifier(["mob.apps"]).verify("tok")


def test_google_verifier_requires_client_ids() -> None:
    with pytest.raises(ValueError, match="at least one client id"):
        GoogleTokenVerifier([])
