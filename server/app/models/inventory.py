import uuid
from datetime import datetime, timezone
from typing import Optional

from sqlmodel import Field, SQLModel


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class InventoryItem(SQLModel, table=True):
    __tablename__ = "inventory_items"

    id: uuid.UUID = Field(default_factory=uuid.uuid4, primary_key=True)
    session_id: uuid.UUID = Field(foreign_key="survey_sessions.id")
    source_image_id: Optional[uuid.UUID] = Field(
        default=None, foreign_key="uploaded_images.id"
    )

    item_name: str
    category: Optional[str] = None
    quantity: Optional[int] = None
    needs_to_ship: Optional[bool] = None
    confidence_score: Optional[float] = None

    estimated_weight_kg: Optional[float] = None
    estimated_height_cm: Optional[float] = None
    estimated_width_cm: Optional[float] = None
    estimated_depth_cm: Optional[float] = None
    estimated_value: Optional[float] = None
    estimated_material: Optional[str] = None

    is_fragile: Optional[bool] = None
    needs_disassembly: Optional[bool] = None
    needs_special_handling: Optional[bool] = None

    packing_difficulty: Optional[str] = None
    lifting_difficulty: Optional[str] = None
    room_location: Optional[str] = None
    condition: Optional[str] = None
    remarks: Optional[str] = None

    created_at: datetime = Field(default_factory=_utcnow)
