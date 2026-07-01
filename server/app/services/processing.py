import logging
from datetime import datetime, timezone
from uuid import UUID

from sqlmodel import Session, select

from app.database.engine import engine
from app.models.image import UploadedImage
from app.models.inventory import InventoryItem
from app.models.survey import SurveySession
from app.services.gemini import analyze_images
from app.services.storage import download_image

logger = logging.getLogger(__name__)


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def run_survey_analysis(session_id: UUID) -> None:
    with Session(engine) as db:
        survey = db.get(SurveySession, session_id)
        if not survey:
            logger.error("Survey session %s not found", session_id)
            return

        survey.status = "processing"
        survey.updated_at = _utcnow()
        db.add(survey)
        db.commit()

        try:
            images = db.exec(
                select(UploadedImage).where(UploadedImage.session_id == session_id)
            ).all()

            image_data = [
                (download_image(img.gcs_uri), img.mime_type) for img in images
            ]

            result = analyze_images(image_data)

            for gemini_item in result.items:
                item = InventoryItem(
                    session_id=session_id,
                    item_name=gemini_item.itemName,
                    category=gemini_item.category,
                    quantity=gemini_item.quantity,
                    needs_to_ship=gemini_item.needsToShip,
                    confidence_score=gemini_item.confidenceScore,
                    estimated_weight_kg=gemini_item.estimatedWeightKg,
                    estimated_height_cm=gemini_item.estimatedHeightCm,
                    estimated_width_cm=gemini_item.estimatedWidthCm,
                    estimated_depth_cm=gemini_item.estimatedDepthCm,
                    estimated_value=gemini_item.estimatedValue,
                    estimated_material=gemini_item.estimatedMaterial,
                    is_fragile=gemini_item.isFragile,
                    needs_disassembly=gemini_item.needsDisassembly,
                    needs_special_handling=gemini_item.needsSpecialHandling,
                    packing_difficulty=gemini_item.packingDifficulty,
                    lifting_difficulty=gemini_item.liftingDifficulty,
                    room_location=gemini_item.roomLocation,
                    condition=gemini_item.condition,
                    remarks=gemini_item.remarks,
                )
                db.add(item)

            survey.status = "completed"
            survey.needs_more_images = result.needsMoreImages
            survey.requested_images = result.requestedImages or None
            survey.updated_at = _utcnow()
            db.add(survey)
            db.commit()

            logger.info(
                "Survey %s completed — %d items detected", session_id, len(result.items)
            )

        except Exception as exc:
            logger.exception("Survey %s failed: %s", session_id, exc)
            db.rollback()

            # Re-fetch after rollback since the session state was cleared.
            survey = db.get(SurveySession, session_id)
            if survey:
                survey.status = "failed"
                survey.error_message = str(exc)
                survey.updated_at = _utcnow()
                db.add(survey)
                db.commit()
