"""item_media junction table.

Many-to-many link between ``survey_items`` and ``media``: an AI-detected item may
be evidenced by several images/frames, and a single image may contain several
items. Modelled as a plain association table (no extra payload) with a composite
primary key that also prevents duplicate links.
"""

from __future__ import annotations

from sqlalchemy import Column, DateTime, ForeignKey, Table, func

from app.db.base import Base

item_media = Table(
    "item_media",
    Base.metadata,
    Column(
        "item_id",
        ForeignKey("survey_items.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    Column(
        "media_id",
        ForeignKey("media.id", ondelete="CASCADE"),
        primary_key=True,
    ),
    Column("created_at", DateTime(timezone=True), server_default=func.now(), nullable=False),
)
