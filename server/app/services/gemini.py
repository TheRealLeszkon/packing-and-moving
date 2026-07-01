import json
import logging

from google import genai
from google.genai import types

from app.config import settings
from app.prompts.survey import SURVEY_SYSTEM_PROMPT
from app.schemas.gemini import GeminiSurveyResponse

logger = logging.getLogger(__name__)

_client: genai.Client | None = None


def _get_client() -> genai.Client:
    global _client
    if _client is None:
        _client = genai.Client(api_key=settings.gemini_api_key)
    return _client


def analyze_images(image_data: list[tuple[bytes, str]]) -> GeminiSurveyResponse:
    """
    Send images to Gemini and return a validated survey response.

    image_data: list of (raw_bytes, mime_type) tuples, one per image.
    """
    client = _get_client()

    parts: list[types.Part] = [
        types.Part.from_bytes(data=image_bytes, mime_type=mime_type)
        for image_bytes, mime_type in image_data
    ]
    # Explicit instruction so Gemini knows what is expected of it.
    parts.append(
        types.Part.from_text(
            text="Identify and estimate all visible movable items in these images."
        )
    )

    response = client.models.generate_content(
        model=settings.model_name,
        contents=types.Content(role="user", parts=parts),
        config=types.GenerateContentConfig(
            system_instruction=SURVEY_SYSTEM_PROMPT,
            response_mime_type="application/json",
        ),
    )

    logger.debug("Gemini raw response: %.500s", response.text)

    data = json.loads(response.text)
    return GeminiSurveyResponse.model_validate(data)
