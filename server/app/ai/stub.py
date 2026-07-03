"""Stub AI provider.

Returns a deterministic, schema-valid analysis without any network calls. Used by
tests and local runs (``AI_PROVIDER=stub``) so the full pipeline — dispatch,
persistence, item/media linking, survey finalisation — can be exercised without
spending Gemini tokens or requiring an API key.
"""

from __future__ import annotations

from app.ai.base import AIAnalysisResult, ImageInput, TokenUsage
from app.ai.prompts import ACTIVE_PROMPT_VERSION
from app.ai.schema import AIDetectedItem, AISurveyAnalysis


class StubAIProvider:
    name = "stub"

    def analyze(
        self, images: list[ImageInput], *, prompt_version: str | None = None
    ) -> AIAnalysisResult:
        # One representative item, evidenced by the first image, so item_media
        # linking is exercised.
        analysis = AISurveyAnalysis(
            items=[
                AIDetectedItem(
                    item_name="Two-seater sofa",
                    category="Furniture",
                    quantity=1,
                    room_location="Living Room",
                    needs_to_ship=True,
                    confidence_score=0.82,
                    weight_kg=45.0,
                    height_cm=85.0,
                    width_cm=150.0,
                    depth_cm=90.0,
                    estimated_value=400.0,
                    material="Fabric",
                    fragile=False,
                    needs_disassembly=False,
                    needs_special_handling=True,
                    packing_difficulty="medium",
                    lifting_difficulty="hard",
                    condition="Good",
                    remarks="Stub-generated item for testing.",
                    source_image_indexes=[0] if images else [],
                )
            ],
            needs_more_images=False,
        )
        return AIAnalysisResult(
            analysis=analysis,
            model="stub",
            prompt_version=prompt_version or ACTIVE_PROMPT_VERSION,
            usage=TokenUsage(prompt_tokens=0, completion_tokens=0, total_tokens=0),
            latency_ms=0,
            raw_response=analysis.model_dump(by_alias=True),
        )
