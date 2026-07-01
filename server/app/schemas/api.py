from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class UploadResponse(BaseModel):
    session_id: UUID
    status: str
    image_count: int
    message: str


class ImageResponse(BaseModel):
    id: UUID
    gcs_uri: str
    original_filename: str
    mime_type: str
    created_at: datetime

    model_config = {"from_attributes": True}


class InventoryItemResponse(BaseModel):
    id: UUID
    item_name: str
    category: Optional[str]
    quantity: Optional[int]
    needs_to_ship: Optional[bool]
    confidence_score: Optional[float]
    estimated_weight_kg: Optional[float]
    estimated_height_cm: Optional[float]
    estimated_width_cm: Optional[float]
    estimated_depth_cm: Optional[float]
    estimated_value: Optional[float]
    estimated_material: Optional[str]
    is_fragile: Optional[bool]
    needs_disassembly: Optional[bool]
    needs_special_handling: Optional[bool]
    packing_difficulty: Optional[str]
    lifting_difficulty: Optional[str]
    room_location: Optional[str]
    condition: Optional[str]
    remarks: Optional[str]
    created_at: datetime

    model_config = {"from_attributes": True}


class ProcessingResponse(BaseModel):
    session_id: UUID
    status: str
    created_at: datetime
    updated_at: datetime
    images: list[ImageResponse]
    items: list[InventoryItemResponse]
    needs_more_images: Optional[bool]
    requested_images: Optional[list[str]]
    error_message: Optional[str]


class ProcessingSummary(BaseModel):
    session_id: UUID
    status: str
    created_at: datetime
    updated_at: datetime
    image_count: int
    item_count: int
