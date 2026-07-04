package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.ItemMergeRequest
import com.packingandmoving.surveyagent.model.ItemSplitRequest
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyItemCreate
import com.packingandmoving.surveyagent.model.SurveyItemList
import com.packingandmoving.surveyagent.model.SurveyItemUpdate

/**
 * Inventory items. Editing is only permitted for the assigned surveyor while the survey is
 * ready_for_review or revision_required (frontend-integration.md §10); the backend enforces
 * this and the repository surfaces the resulting error.
 */
class ItemRepository(private val api: SurveyAgentApi) {

    suspend fun listItems(surveyId: String): ApiResult<SurveyItemList> =
        safeApiCall { api.getItems(surveyId) }

    suspend fun addItem(surveyId: String, body: SurveyItemCreate): ApiResult<SurveyItem> =
        safeApiCall { api.addItem(surveyId, body) }

    suspend fun updateItem(itemId: String, body: SurveyItemUpdate): ApiResult<SurveyItem> =
        safeApiCall { api.updateItem(itemId, body) }

    suspend fun deleteItem(itemId: String): ApiResult<MessageResponse> =
        safeApiCall { api.deleteItem(itemId) }

    suspend fun mergeItems(body: ItemMergeRequest): ApiResult<SurveyItem> =
        safeApiCall { api.mergeItems(body) }

    suspend fun splitItem(itemId: String, body: ItemSplitRequest): ApiResult<SurveyItemList> =
        safeApiCall { api.splitItem(itemId, body) }
}
