"""Gemini implementation of :class:`AIProvider`.

Wraps ``google-genai`` (imported lazily so the dependency is only required when
Gemini is actually used). Sends the processed images plus a versioned system
prompt, asks for a JSON response, then parses and validates it into
``AISurveyAnalysis``. Transient failures (network, rate-limit, malformed JSON) are
retried with exponential backoff; anything still failing raises
``AIProviderError`` for the caller to handle gracefully.
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any

from app.ai.base import AIAnalysisResult, AIProviderError, ImageInput, TokenUsage
from app.ai.prompts import ACTIVE_PROMPT_VERSION, USER_INSTRUCTION, get_prompt
from app.ai.schema import AISurveyAnalysis

logger = logging.getLogger(__name__)


class GeminiProvider:
    name = "gemini"

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout_seconds: int = 120,
        max_retries: int = 2,
    ) -> None:
        self._api_key = api_key
        self._model = model
        self._timeout_ms = timeout_seconds * 1000
        self._max_retries = max_retries
        self._client: Any | None = None

    def _get_client(self) -> Any:
        if self._client is None:
            from google import genai  # lazy: only needed when Gemini runs
            from google.genai import types

            self._client = genai.Client(
                api_key=self._api_key,
                http_options=types.HttpOptions(timeout=self._timeout_ms),
            )
        return self._client

    def analyze(
        self, images: list[ImageInput], *, prompt_version: str | None = None
    ) -> AIAnalysisResult:
        if not images:
            raise AIProviderError("No images supplied for analysis.")

        from google.genai import types

        version = prompt_version or ACTIVE_PROMPT_VERSION
        system_prompt = get_prompt(version)
        client = self._get_client()

        parts: list[Any] = [
            types.Part.from_bytes(data=img.data, mime_type=img.mime_type)
            for img in images
        ]
        parts.append(types.Part.from_text(text=USER_INSTRUCTION))
        contents = types.Content(role="user", parts=parts)
        config = types.GenerateContentConfig(
            system_instruction=system_prompt,
            response_mime_type="application/json",
        )

        last_error: Exception | None = None
        for attempt in range(1, self._max_retries + 2):  # 1 try + max_retries
            start = time.monotonic()
            try:
                response = client.models.generate_content(
                    model=self._model, contents=contents, config=config
                )
                latency_ms = int((time.monotonic() - start) * 1000)
                payload = _parse_json(response.text)
                analysis = AISurveyAnalysis.model_validate(payload)
                return AIAnalysisResult(
                    analysis=analysis,
                    model=self._model,
                    prompt_version=version,
                    usage=_extract_usage(response),
                    latency_ms=latency_ms,
                    raw_response=payload,
                )
            except Exception as exc:  # noqa: BLE001 - retried / re-raised below
                last_error = exc
                logger.warning(
                    "gemini_attempt_failed",
                    extra={"attempt": attempt, "error": str(exc)[:500]},
                )
                if attempt <= self._max_retries:
                    time.sleep(min(2 ** (attempt - 1), 8))

        raise AIProviderError(
            f"Gemini analysis failed after {self._max_retries + 1} attempts: {last_error}"
        ) from last_error


def _parse_json(text: str | None) -> dict[str, Any]:
    if not text:
        raise ValueError("Empty response from model.")
    data = json.loads(text)
    if not isinstance(data, dict):
        raise ValueError("Model response was not a JSON object.")
    return data


def _extract_usage(response: Any) -> TokenUsage:
    meta = getattr(response, "usage_metadata", None)
    if meta is None:
        return TokenUsage()
    return TokenUsage(
        prompt_tokens=getattr(meta, "prompt_token_count", None),
        completion_tokens=getattr(meta, "candidates_token_count", None),
        total_tokens=getattr(meta, "total_token_count", None),
    )
