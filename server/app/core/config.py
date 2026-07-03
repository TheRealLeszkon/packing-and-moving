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

    # ---- Database ----
    db_url: str
    db_username: str | None = None
    db_password: str | None = None
    db_pool_size: int = 10
    db_max_overflow: int = 20
    db_echo: bool = False

    # ---- Auth / JWT ----
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    access_token_ttl_minutes: int = 30
    refresh_token_ttl_days: int = 30
    # How incoming ID tokens are verified. Defaults to DEV for local work; a
    # validator forbids DEV in production so it can never ship by accident.
    auth_mode: AuthMode = AuthMode.DEV
    google_oauth_client_ids: list[str] = Field(default_factory=list)

    # ---- Redis ----
    redis_url: str = "redis://localhost:6379/0"

    # ---- Google Cloud ----
    gcp_project_id: str
    gcs_bucket_name: str
    google_application_credentials: str | None = None

    # ---- AI ----
    gemini_api_key: str | None = None
    gemini_model: str = "gemini-2.5-flash"

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

    @model_validator(mode="after")
    def _guard_auth_mode(self) -> Settings:
        """Fail fast if a production deployment tries to use the dev verifier, or
        enables Google auth without any allowed client IDs."""
        if self.is_production and self.auth_mode is AuthMode.DEV:
            raise ValueError("auth_mode=dev is not permitted when APP_ENV=production")
        if self.auth_mode is AuthMode.GOOGLE and not self.google_oauth_client_ids:
            raise ValueError("auth_mode=google requires GOOGLE_OAUTH_CLIENT_IDS to be set")
        return self

    @property
    def is_production(self) -> bool:
        return self.app_env is AppEnv.PRODUCTION

    @property
    def sqlalchemy_url(self) -> URL:
        """Build the async SQLAlchemy URL (postgresql+asyncpg).

        Credentials may live inside ``DB_URL`` already, or be supplied separately
        via ``DB_USERNAME`` / ``DB_PASSWORD``. The scheme is always normalised to
        the asyncpg driver used by the API engine.
        """
        url = make_url(self.db_url).set(drivername="postgresql+asyncpg")
        if url.username is None and self.db_username:
            url = url.set(username=self.db_username)
        if url.password is None and self.db_password:
            url = url.set(password=self.db_password)
        return url


@lru_cache
def get_settings() -> Settings:
    """Cached settings accessor, safe to use as a FastAPI dependency."""
    return Settings()  # type: ignore[call-arg]  # values come from the environment


settings = get_settings()
