import logging
import uuid

from google.cloud import storage

from app.config import settings

logger = logging.getLogger(__name__)

_client: storage.Client | None = None


def _get_client() -> storage.Client:
    global _client
    if _client is None:
        _client = storage.Client(project=settings.gcp_project_id)
    return _client


def upload_image(file_bytes: bytes, original_filename: str, mime_type: str) -> str:
    client = _get_client()
    bucket = client.bucket(settings.gcs_bucket_name)

    ext = original_filename.rsplit(".", 1)[-1] if "." in original_filename else "jpg"
    blob_name = f"{settings.gcs_bucket_file_name}/{uuid.uuid4()}.{ext}"

    blob = bucket.blob(blob_name)
    blob.upload_from_string(file_bytes, content_type=mime_type)

    gcs_uri = f"gs://{settings.gcs_bucket_name}/{blob_name}"
    logger.info("Uploaded %s to %s", original_filename, gcs_uri)
    return gcs_uri


def download_image(gcs_uri: str) -> bytes:
    client = _get_client()
    # Strip gs:// prefix and split into bucket + path
    path = gcs_uri.removeprefix("gs://")
    bucket_name, blob_name = path.split("/", 1)

    blob = client.bucket(bucket_name).blob(blob_name)
    return blob.download_as_bytes()
