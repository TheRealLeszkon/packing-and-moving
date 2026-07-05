"""Application configuration.

All runtime configuration is read from environment variables (or a local ``.env``)
via ``pydantic-settings``. Nothing is hard-coded and no secret has a usable
default. Import the singleton ``settings`` (or the cached ``get_settings()``
dependency) rather than reading ``os.environ`` directly.
"""

from __future__ import annotations

from enum import StrEnum
from functools import lru_cache

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy import URL, make_url


class AppEnv(StrEnum):
    DEVELOPMENT = "development"
    STAGING = "staging"
    PRODUCTION = "production"


class AuthMode(StrEnum):
    GOOGLE = "google"  # verify real Google ID tokens (requires OAuth setup)
    DEV = "dev"        # accept locally-minted dev tokens; never allowed in prod


class StorageBackend(StrEnum):
    GCS = "gcs"        # Google Cloud Storage (production)
    MEMORY = "memory"  # in-process store for local dev / tests (no external calls)


class AIProviderName(StrEnum):
    GEMINI = "gemini"  # Google Gemini (production)
    STUB = "stub"      # deterministic canned response for local dev / tests (no API calls)


class Settings(BaseSettings):
    """Typed, validated view of the process environment."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---- Application ----
    app_env: AppEnv = AppEnv.DEVELOPMENT
    log_level: str = "INFO"
    log_json: bool = True
    # Demo-only: expose ``POST /users/me/role`` so one account can self-switch
    # between customer and surveyor for testing/demos. Defaults on for local work;
    # set ALLOW_SELF_ROLE_CHANGE=false in production to hide the endpoint.
    allow_self_role_change: bool = True

    # ---- Database ----
    db_url: str
    db_username: str | None = None
    db_password: str | None = None
    db_pool_size: int = 10
    db_max_overflow: int = 20
    db_echo: bool = False
    # Use a non-pooling engine (NullPool). Required under pytest, where each test
    # runs on its own event loop and a pooled asyncpg connection from a previous
    # loop cannot be reused. Left off in production for connection pooling.
    db_use_nullpool: bool = False

    # ---- Auth / JWT ----
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    access_token_ttl_minutes: int = 30
    refresh_token_ttl_days: int = 30
    # How incoming ID tokens are verified. Defaults to DEV for local work; a
    # validator forbids DEV in production so it can never ship by accident.
    auth_mode: AuthMode = AuthMode.DEV
    # Google OAuth client IDs allowed to issue ID tokens. Provide any of:
    #   - GOOGLE_OAUTH_CLIENT_IDS (comma-separated), and/or
    #   - MOBILE_CLIENT_ID / WEB_CLIENT_ID (the individual client IDs created in
    #     the Google Cloud console). All are merged into the accepted audiences.
    google_oauth_client_ids: list[str] = Field(default_factory=list)
    mobile_client_id: str | None = None
    web_client_id: str | None = None
    # OAuth client secret for the web client. Only needed for the authorization-
    # code exchange (server-side sign-in); ID-token verification does not use it.
    web_client_secret: str | None = None

    # ---- Redis ----
    redis_url: str = "redis://localhost:6379/0"

    # ---- Processing pipeline ----
    # Whether the API dispatches processing jobs to the broker after commit.
    # Disable in tests that exercise the API without a running worker/Redis.
    processing_dispatch_enabled: bool = True
    image_max_width: int = 1280
    image_max_height: int = 960
    video_frame_rate: float = 1.0          # frames per second to extract
    blur_variance_threshold: float = 100.0  # Laplacian variance below this = blurry
    dedup_hamming_threshold: int = 5        # perceptual-hash distance <= this = duplicate
    job_max_attempts: int = 3

    # ---- Storage ----
    storage_backend: StorageBackend = StorageBackend.GCS
    signed_url_ttl_seconds: int = 900  # 15 min

    # ---- Google Cloud ----
    gcp_project_id: str
    gcs_bucket_name: str
    google_application_credentials: str | None = None

    # ---- AI ----
    # Which provider analyses processed media. Defaults to Gemini; STUB returns a
    # deterministic canned result for tests/local runs without an API key.
    ai_provider: AIProviderName = AIProviderName.GEMINI
    gemini_api_key: str | None = None
    gemini_model: str = "gemini-2.5-flash"
    # Cap the number of images sent to the model in one analysis (cost/latency).
    ai_max_images: int = 30
    # Upper bound on items accepted from a single AI response (guards against a
    # malformed/adversarial response trying to insert thousands of rows).
    ai_max_items: int = 200
    ai_request_timeout_seconds: int = 120
    # Retries for *transient* provider errors, applied inside the provider.
    ai_max_retries: int = 2

    # ---- Rate limiting ----
    # In-process sliding-window limiter. For multi-instance deployments back this
    # with Redis; the limiter interface is designed to be swapped.
    rate_limit_enabled: bool = True
    rate_limit_requests: int = 120        # general requests per window per client
    rate_limit_window_seconds: int = 60
    rate_limit_auth_requests: int = 20    # tighter budget for /auth endpoints

    # ---- Upload limits ----
    max_image_size_mb: int = 25
    max_video_size_mb: int = 200
    max_video_duration_seconds: int = 30

    @field_validator("google_oauth_client_ids", mode="before")
    @classmethod
    def _split_client_ids(cls, value: object) -> object:
        """Accept a comma-separated string as well as a JSON/list value."""
        if isinstance(value, str):
            return [item.strip() for item in value.split(",") if item.strip()]
        return value

    @property
    def allowed_google_client_ids(self) -> list[str]:
        """Every Google OAuth client ID accepted as a token audience.

        Merges the comma-separated ``GOOGLE_OAUTH_CLIENT_IDS`` with the individual
        ``MOBILE_CLIENT_ID`` / ``WEB_CLIENT_ID`` values, de-duplicated and order-
        preserving so config can be expressed either way.
        """
        merged = [
            *self.google_oauth_client_ids,
            *(cid for cid in (self.mobile_client_id, self.web_client_id) if cid),
        ]
        return list(dict.fromkeys(merged))

    @model_validator(mode="after")
    def _guard_auth_mode(self) -> Settings:
        """Fail fast if a production deployment tries to use the dev verifier, or
        enables Google auth without any allowed client IDs."""
        if self.is_production and self.auth_mode is AuthMode.DEV:
            raise ValueError("auth_mode=dev is not permitted when APP_ENV=production")
        if self.auth_mode is AuthMode.GOOGLE and not self.allowed_google_client_ids:
            raise ValueError(
                "auth_mode=google requires GOOGLE_OAUTH_CLIENT_IDS or "
                "MOBILE_CLIENT_ID / WEB_CLIENT_ID to be set"
            )
        if (
            self.is_production
            and self.ai_provider is AIProviderName.GEMINI
            and not self.gemini_api_key
        ):
            raise ValueError("ai_provider=gemini requires GEMINI_API_KEY in production")
        return self

    @property
    def is_production(self) -> bool:
        return self.app_env is AppEnv.PRODUCTION

    def _db_url(self, driver: str) -> URL:
        """Build a SQLAlchemy URL with the given driver, filling in credentials.

        Credentials may live inside ``DB_URL`` already, or be supplied separately
        via ``DB_USERNAME`` / ``DB_PASSWORD``.
        """
        url = make_url(self.db_url).set(drivername=driver)
        if url.username is None and self.db_username:
            url = url.set(username=self.db_username)
        if url.password is None and self.db_password:
            url = url.set(password=self.db_password)
        return url

    @property
    def sqlalchemy_url(self) -> URL:
        """Async URL (asyncpg) used by the API engine."""
        return self._db_url("postgresql+asyncpg")

    @property
    def sync_sqlalchemy_url(self) -> URL:
        """Sync URL (psycopg2) used by the background worker sessions."""
        return self._db_url("postgresql+psycopg2")


@lru_cache
def get_settings() -> Settings:
    """Cached settings accessor, safe to use as a FastAPI dependency."""
    return Settings()  # type: ignore[call-arg]  # values come from the environment


settings = get_settings()
