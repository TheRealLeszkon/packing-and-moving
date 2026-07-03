import logging
from uuid import UUID

from fastapi import APIRouter, BackgroundTasks, Depends, File, HTTPException, Response, UploadFile
from sqlmodel import Session, select

from app.database.engine import get_session
from app.models.image import UploadedImage
from app.models.inventory import InventoryItem
from app.models.survey import SurveySession
from app.schemas.api import (
    ImageResponse,
    InventoryItemResponse,
    ProcessingResponse,
    ProcessingSummary,
    UploadResponse,
)
from app.services.processing import run_survey_analysis
from app.services.storage import download_image, upload_image

router = APIRouter()
logger = logging.getLogger(__name__)

ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"}


def _image_response(image: UploadedImage) -> ImageResponse:
    return ImageResponse(
        id=image.id,
        url=f"/images/{image.id}",
        original_filename=image.original_filename,
        mime_type=image.mime_type,
        created_at=image.created_at,
    )


def _item_response(item: InventoryItem) -> InventoryItemResponse:
    return InventoryItemResponse(
        **item.model_dump(),
        image_url=f"/images/{item.source_image_id}" if item.source_image_id else None,
    )


@router.post("/upload", response_model=UploadResponse, status_code=202)
async def upload_images(
    background_tasks: BackgroundTasks,
    files: list[UploadFile] = File(...),
    db: Session = Depends(get_session),
) -> UploadResponse:
    if not files:
        raise HTTPException(status_code=400, detail="At least one image file is required.")

    for f in files:
        if f.content_type not in ALLOWED_MIME_TYPES:
            raise HTTPException(
                status_code=415,
                detail=f"Unsupported file type '{f.content_type}'. Accepted: {', '.join(sorted(ALLOWED_MIME_TYPES))}",
            )

    survey = SurveySession()
    db.add(survey)
    db.commit()
    db.refresh(survey)

    for f in files:
        file_bytes = await f.read()
        gcs_uri = upload_image(
            file_bytes,
            original_filename=f.filename or "upload",
            mime_type=f.content_type,
        )
        db.add(
            UploadedImage(
                session_id=survey.id,
                gcs_uri=gcs_uri,
                original_filename=f.filename or "upload",
                mime_type=f.content_type,
            )
        )

    db.commit()

    background_tasks.add_task(run_survey_analysis, survey.id)
    logger.info("Survey %s created with %d image(s)", survey.id, len(files))

    return UploadResponse(
        session_id=survey.id,
        status=survey.status,
        image_count=len(files),
        message="Images uploaded successfully. AI analysis is running in the background.",
    )


@router.get("/processing/{session_id}", response_model=ProcessingResponse)
def get_processing(
    session_id: UUID,
    db: Session = Depends(get_session),
) -> ProcessingResponse:
    survey = db.get(SurveySession, session_id)
    if not survey:
        raise HTTPException(status_code=404, detail="Survey session not found.")

    images = db.exec(
        select(UploadedImage).where(UploadedImage.session_id == session_id)
    ).all()

    items = db.exec(
        select(InventoryItem).where(InventoryItem.session_id == session_id)
    ).all()

    return ProcessingResponse(
        session_id=survey.id,
        status=survey.status,
        created_at=survey.created_at,
        updated_at=survey.updated_at,
        images=[_image_response(img) for img in images],
        items=[_item_response(item) for item in items],
        needs_more_images=survey.needs_more_images,
        requested_images=survey.requested_images,
        error_message=survey.error_message,
    )


@router.get("/images/{image_id}")
def get_image(image_id: UUID, db: Session = Depends(get_session)) -> Response:
    image = db.get(UploadedImage, image_id)
    if not image:
        raise HTTPException(status_code=404, detail="Image not found.")

    file_bytes = download_image(image.gcs_uri)
    return Response(content=file_bytes, media_type=image.mime_type)


@router.get("/processing", response_model=list[ProcessingSummary])
def list_processing(db: Session = Depends(get_session)) -> list[ProcessingSummary]:
    surveys = db.exec(
        select(SurveySession).order_by(SurveySession.created_at.desc())
    ).all()

    results = []
    for survey in surveys:
        image_count = db.exec(
            select(UploadedImage).where(UploadedImage.session_id == survey.id)
        ).all()
        item_count = db.exec(
            select(InventoryItem).where(InventoryItem.session_id == survey.id)
        ).all()
        results.append(
            ProcessingSummary(
                session_id=survey.id,
                status=survey.status,
                created_at=survey.created_at,
                updated_at=survey.updated_at,
                image_count=len(image_count),
                item_count=len(item_count),
            )
        )

    return results
