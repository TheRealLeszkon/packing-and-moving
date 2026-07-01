import os
from urllib.parse import urlparse, urlunparse
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    gemini_api_key: str
    model_name: str = "gemini-2.5-flash"

    db_url: str
    db_username: str
    db_password: str

    gcp_project_id: str
    gcs_bucket_name: str
    gcs_bucket_file_name: str = "images"
    google_application_credentials: str

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    @property
    def database_url(self) -> str:
        parsed = urlparse(self.db_url)
        if parsed.username:
            # Credentials already present — just ensure the psycopg2 driver prefix
            return self.db_url.replace("postgresql://", "postgresql+psycopg2://", 1)
        netloc = f"{self.db_username}:{self.db_password}@{parsed.hostname}"
        if parsed.port:
            netloc += f":{parsed.port}"
        return urlunparse(parsed._replace(scheme="postgresql+psycopg2", netloc=netloc))


settings = Settings()

# The Google SDK reads this env var at import time; ensure it is set.
os.environ.setdefault("GOOGLE_APPLICATION_CREDENTIALS", settings.google_application_credentials)
