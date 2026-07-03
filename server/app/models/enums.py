"""Domain enumerations.

All enums are ``StrEnum`` so their values serialise as human-readable strings in
JSON and in the database. Columns store them via SQLAlchemy's ``Enum(...,
native_enum=False)`` (see ``app.db.types``), i.e. as ``VARCHAR`` + ``CHECK`` —
this keeps migrations simple (adding a value is not a fragile ``ALTER TYPE``).
"""

from __future__ import annotations

from enum import StrEnum


class UserRole(StrEnum):
    CUSTOMER = "customer"
    SURVEYOR = "surveyor"
    ADMIN = "admin"  # reserved for internal/admin operations (Phase 8)


class SurveyStatus(StrEnum):
    """Canonical survey lifecycle states (see CLAUDE.md)."""

    SCHEDULED = "scheduled"                        # created by customer, unassigned
    ASSIGNED = "assigned"                          # a surveyor accepted it
    IN_PROGRESS = "in_progress"                    # surveyor on-site / uploading
    PROCESSING = "processing"                      # media + AI pipeline running
    READY_FOR_REVIEW = "ready_for_review"          # AI done, awaiting surveyor review
    AWAITING_CUSTOMER_APPROVAL = "awaiting_customer_approval"
    REVISION_REQUIRED = "revision_required"        # customer requested changes
    APPROVED = "approved"                          # customer approved
    COMPLETED = "completed"                        # terminal success
    CANCELLED = "cancelled"                        # terminal cancellation


class MediaType(StrEnum):
    IMAGE = "image"                 # original uploaded image
    VIDEO = "video"                 # original uploaded video
    PROCESSED_IMAGE = "processed_image"  # resized/normalised image derived from an image
    EXTRACTED_FRAME = "extracted_frame"  # frame extracted from a video


class ProcessingStatus(StrEnum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"     # e.g. a blurry/duplicate frame dropped from the pipeline


class JobType(StrEnum):
    IMAGE_RESIZE = "image_resize"
    VIDEO_FRAME_EXTRACTION = "video_frame_extraction"
    BLUR_DETECTION = "blur_detection"
    DUPLICATE_DETECTION = "duplicate_detection"
    AI_ANALYSIS = "ai_analysis"


class JobStatus(StrEnum):
    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    DEAD_LETTER = "dead_letter"   # exhausted retries; needs manual attention


class AIRunStatus(StrEnum):
    PENDING = "pending"
    SUCCEEDED = "succeeded"
    FAILED = "failed"


class Difficulty(StrEnum):
    EASY = "easy"
    MEDIUM = "medium"
    HARD = "hard"


class ItemCondition(StrEnum):
    NEW = "new"
    GOOD = "good"
    FAIR = "fair"
    POOR = "poor"
    DAMAGED = "damaged"
