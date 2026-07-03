"""AI provider selection.

Returns the configured provider (cached). Gemini is used in production; the stub
is selected explicitly via ``AI_PROVIDER=stub`` or implicitly when no
``GEMINI_API_KEY`` is set, so the app never crashes for lack of a key in dev —
it degrades to deterministic canned analysis instead.
"""

from __future__ import annotations

import logging
from functools import lru_cache

from app.ai.base import AIProvider
from app.core.config import AIProviderName, settings

logger = logging.getLogger(__name__)


@lru_cache
def get_ai_provider() -> AIProvider:
    if settings.ai_provider is AIProviderName.STUB or not settings.gemini_api_key:
        if settings.ai_provider is AIProviderName.GEMINI:
            logger.warning("ai_provider_fallback_stub", extra={"reason": "no GEMINI_API_KEY"})
        from app.ai.stub import StubAIProvider

        return StubAIProvider()

    from app.ai.gemini import GeminiProvider

    return GeminiProvider(
        api_key=settings.gemini_api_key,
        model=settings.gemini_model,
        timeout_seconds=settings.ai_request_timeout_seconds,
        max_retries=settings.ai_max_retries,
    )
