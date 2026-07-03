"""AI provider interface.

The pipeline depends on ``AIProvider`` (this abstraction), never on a concrete
model SDK. Implementations are *synchronous* because they are called from the
sync worker; a provider is responsible for its own transient-error retries and
request timeout. The result carries everything needed to persist an
``ai_analysis_runs`` audit row: the validated analysis, the raw JSON, the model +
prompt version used, token usage, and latency.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

from app.ai.schema import AISurveyAnalysis


class AIProviderError(Exception):
    """Raised when analysis fails after the provider's own retries are exhausted."""


@dataclass(frozen=True, slots=True)
class ImageInput:
    """One image to analyse: raw processed bytes plus its MIME type."""

    data: bytes
    mime_type: str = "image/jpeg"


@dataclass(frozen=True, slots=True)
class TokenUsage:
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    total_tokens: int | None = None


@dataclass(frozen=True, slots=True)
class AIAnalysisResult:
    analysis: AISurveyAnalysis
    model: str
    prompt_version: str
    usage: TokenUsage = field(default_factory=TokenUsage)
    latency_ms: int = 0
    # The provider's raw JSON response, persisted verbatim for reproducibility.
    raw_response: dict[str, Any] = field(default_factory=dict)


class AIProvider(Protocol):
    name: str

    def analyze(
        self, images: list[ImageInput], *, prompt_version: str | None = None
    ) -> AIAnalysisResult:
        """Analyse ``images`` and return a validated, provenance-carrying result.

        Raises ``AIProviderError`` on unrecoverable failure.
        """
        ...
