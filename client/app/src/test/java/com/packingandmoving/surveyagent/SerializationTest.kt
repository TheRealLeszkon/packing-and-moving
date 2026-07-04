package com.packingandmoving.surveyagent

import com.packingandmoving.surveyagent.api.ApiEnvelope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyItemUpdate
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.model.TokenResponse
import com.packingandmoving.surveyagent.model.UserRole
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the DTOs decode/encode against the exact wire format from openapi.json:
 * the success envelope, snake_case <-> camelCase mapping, enum values, Decimal-as-string
 * fields, and null-omitting PATCH bodies. Mirrors [NetworkModule.json].
 */
class SerializationTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes token envelope with snake_case and enum`() {
        val payload = """
            {
              "success": true,
              "data": {
                "access_token": "acc", "refresh_token": "ref", "token_type": "bearer",
                "expires_in": 1800,
                "user": {
                  "id": "u1", "email": "a@b.com", "name": null,
                  "role": "surveyor", "is_active": true,
                  "created_at": "2026-07-04T00:00:00Z"
                }
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString<ApiEnvelope<TokenResponse>>(payload)

        assertTrue(envelope.success)
        assertEquals("acc", envelope.data.accessToken)
        assertEquals(1800, envelope.data.expiresIn)
        assertEquals(UserRole.SURVEYOR, envelope.data.user.role)
        assertTrue(envelope.data.user.isActive)
        assertNull(envelope.data.user.name)
    }

    @Test
    fun `decodes survey item with decimal-as-string and status enum`() {
        val payload = """
            {
              "id": "i1", "survey_id": "s1", "item_name": "Sofa", "category": "furniture",
              "quantity": 2, "room_location": "living_room",
              "height_cm": "80.5", "width_cm": null, "depth_cm": null, "weight_kg": "30.00",
              "material": null, "fragile": true, "needs_disassembly": false,
              "needs_special_handling": false, "needs_to_ship": true,
              "packing_difficulty": "hard", "lifting_difficulty": null,
              "estimated_value": "1200.00", "condition": "good", "remarks": null,
              "confidence_score": "0.92", "source": "ai",
              "created_at": "2026-07-04T00:00:00Z", "updated_at": "2026-07-04T00:00:00Z",
              "media_ids": ["m1", "m2"]
            }
        """.trimIndent()

        val item = json.decodeFromString<SurveyItem>(payload)

        assertEquals("Sofa", item.itemName)
        assertEquals("80.5", item.heightCm)
        assertEquals("0.92", item.confidenceScore)
        assertTrue(item.fragile)
        assertEquals(listOf("m1", "m2"), item.mediaIds)
    }

    @Test
    fun `patch body omits null fields so only changes are sent`() {
        val update = SurveyItemUpdate(itemName = "Renamed", fragile = true)

        val encoded = json.encodeToString(update)

        assertTrue(encoded.contains("\"item_name\":\"Renamed\""))
        assertTrue(encoded.contains("\"fragile\":true"))
        assertFalse("null fields must be omitted from a PATCH", encoded.contains("null"))
        assertFalse(encoded.contains("category"))
    }

    @Test
    fun `survey status enum round-trips`() {
        assertEquals(
            SurveyStatus.READY_FOR_REVIEW,
            json.decodeFromString<SurveyStatus>("\"ready_for_review\""),
        )
    }
}
