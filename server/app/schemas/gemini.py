from typing import Literal, Optional

from pydantic import BaseModel


class GeminiItem(BaseModel):
    itemName: str
    category: Optional[str] = None
    quantity: Optional[int] = None
    needsToShip: Optional[bool] = None
    confidenceScore: Optional[float] = None

    estimatedWeightKg: Optional[float] = None
    estimatedHeightCm: Optional[float] = None
    estimatedWidthCm: Optional[float] = None
    estimatedDepthCm: Optional[float] = None
    estimatedValue: Optional[float] = None
    estimatedMaterial: Optional[str] = None

    isFragile: Optional[bool] = None
    needsDisassembly: Optional[bool] = None
    needsSpecialHandling: Optional[bool] = None

    packingDifficulty: Optional[Literal["Easy", "Medium", "Hard"]] = None
    liftingDifficulty: Optional[Literal["Easy", "Medium", "Hard"]] = None
    roomLocation: Optional[str] = None
    condition: Optional[str] = None
    remarks: Optional[str] = None


class GeminiSurveyResponse(BaseModel):
    items: list[GeminiItem]
    needsMoreImages: bool = False
    requestedImages: list[str] = []
