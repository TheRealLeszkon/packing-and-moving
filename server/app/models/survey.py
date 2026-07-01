import uuid
from datetime import datetime, timezone
from typing import Optional

import sqlalchemy as sa
from sqlmodel import Column, Field, SQLModel


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class SurveySession(SQLModel, table=True):
    __tablename__ = "survey_sessions"

    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    status: str = Field(default="pending")
    created_at: datetime = Field(default_factory=_utcnow)
    updated_at: datetime = Field(default_factory=_utcnow)
    error_message: Optional[str] = None
    needs_more_images: Optional[bool] = None
    requested_images: Optional[list[str]] = Field(
        default=None, sa_column=Column(sa.JSON)
    )
