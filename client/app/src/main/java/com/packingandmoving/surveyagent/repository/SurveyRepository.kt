package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.CancelRequest
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.Page
import com.packingandmoving.surveyagent.model.RejectRequest
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.model.SurveyCreate
import com.packingandmoving.surveyagent.model.SurveyStatusInfo
import com.packingandmoving.surveyagent.model.SurveySummary

/**
 * Surveys, their lifecycle transitions, and the surveyor request board
 * (frontend-integration.md §5, §7). UI should drive available actions off
 * [surveyStatus].availableActions rather than hardcoding transitions.
 */
class SurveyRepository(private val api: SurveyAgentApi) {

    suspend fun createSurvey(body: SurveyCreate): ApiResult<Survey> =
        safeApiCall { api.createSurvey(body) }

    suspend fun mySurveys(limit: Int? = null, offset: Int? = null): ApiResult<Page<Survey>> =
        safeApiCall { api.mySurveys(limit, offset) }

    suspend fun getSurvey(surveyId: String): ApiResult<Survey> =
        safeApiCall { api.getSurvey(surveyId) }

    suspend fun surveyStatus(surveyId: String): ApiResult<SurveyStatusInfo> =
        safeApiCall { api.getSurveyStatus(surveyId) }

    suspend fun summary(surveyId: String): ApiResult<SurveySummary> =
        safeApiCall { api.getSurveySummary(surveyId) }

    suspend fun deleteSurvey(surveyId: String): ApiResult<MessageResponse> =
        safeApiCall { api.deleteSurvey(surveyId) }

    // Lifecycle transitions.
    suspend fun start(surveyId: String): ApiResult<Survey> = safeApiCall { api.startSurvey(surveyId) }

    suspend fun complete(surveyId: String): ApiResult<Survey> = safeApiCall { api.completeSurvey(surveyId) }

    suspend fun submit(surveyId: String): ApiResult<Survey> = safeApiCall { api.submitSurvey(surveyId) }

    suspend fun approve(surveyId: String): ApiResult<Survey> = safeApiCall { api.approveSurvey(surveyId) }

    suspend fun reject(surveyId: String, reason: String): ApiResult<Survey> =
        safeApiCall { api.rejectSurvey(surveyId, RejectRequest(reason)) }

    suspend fun cancel(surveyId: String, reason: String? = null): ApiResult<Survey> =
        safeApiCall { api.cancelSurvey(surveyId, CancelRequest(reason)) }

    // Request board.
    suspend fun availableRequests(limit: Int? = null, offset: Int? = null): ApiResult<Page<Survey>> =
        safeApiCall { api.availableRequests(limit, offset) }

    suspend fun assignedRequests(limit: Int? = null, offset: Int? = null): ApiResult<Page<Survey>> =
        safeApiCall { api.assignedRequests(limit, offset) }

    suspend fun acceptRequest(surveyId: String): ApiResult<Survey> =
        safeApiCall { api.acceptRequest(surveyId) }
}
