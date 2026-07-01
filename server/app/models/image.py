import uuid
from datetime import datetime, timezone

from sqlmodel import Field, SQLModel


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class UploadedImage(SQLModel, table=True):
    __tablename__ = "uploaded_images"

    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    session_id: uuid.UUID = Field(foreign_key="survey_sessions.id")
    gcs_uri: str
    original_filename: str
    mime_type: str
    created_at: datetime = Field(default_factory=_utcnow)
