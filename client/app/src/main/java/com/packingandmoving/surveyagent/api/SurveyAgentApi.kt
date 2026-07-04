package com.packingandmoving.surveyagent.api

import com.packingandmoving.surveyagent.model.CancelRequest
import com.packingandmoving.surveyagent.model.GoogleAuthRequest
import com.packingandmoving.surveyagent.model.ItemMergeRequest
import com.packingandmoving.surveyagent.model.ItemSplitRequest
import com.packingandmoving.surveyagent.model.LogoutRequest
import com.packingandmoving.surveyagent.model.Media
import com.packingandmoving.surveyagent.model.MediaUploadResult
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.Page
import com.packingandmoving.surveyagent.model.RefreshRequest
import com.packingandmoving.surveyagent.model.RejectRequest
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.model.SurveyCreate
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyItemCreate
import com.packingandmoving.surveyagent.model.SurveyItemList
import com.packingandmoving.surveyagent.model.SurveyItemUpdate
import com.packingandmoving.surveyagent.model.ReanalyzeRequest
import com.packingandmoving.surveyagent.model.ReanalyzeResult
import com.packingandmoving.surveyagent.model.SurveyStatusInfo
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.model.TokenResponse
import com.packingandmoving.surveyagent.model.User
import com.packingandmoving.surveyagent.model.UserUpdate
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit surface for the SurveyAgent (surveyor) client. Mirrors openapi.json exactly —
 * every response is wrapped in [ApiEnvelope]; admin-only endpoints are intentionally not
 * modelled. Suspend functions throw retrofit2.HttpException on non-2xx; the repository
 * layer translates that into a typed result.
 */
interface SurveyAgentApi {

    // ---- Auth ----
    @POST("auth/google")
    suspend fun signInWithGoogle(@Body body: GoogleAuthRequest): ApiEnvelope<TokenResponse>

    @POST("auth/refresh")
    suspend fun refreshTokens(@Body body: RefreshRequest): ApiEnvelope<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest): ApiEnvelope<MessageResponse>

    @GET("auth/me")
    suspend fun authMe(): ApiEnvelope<User>

    // ---- Users ----
    @GET("users/me")
    suspend fun getMe(): ApiEnvelope<User>

    @PUT("users/me")
    suspend fun updateMe(@Body body: UserUpdate): ApiEnvelope<User>

    // ---- Surveys ----
    @POST("surveys")
    suspend fun createSurvey(@Body body: SurveyCreate): ApiEnvelope<Survey>

    @GET("surveys/my")
    suspend fun mySurveys(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ApiEnvelope<Page<Survey>>

    @GET("surveys/{survey_id}")
    suspend fun getSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    @GET("surveys/{survey_id}/status")
    suspend fun getSurveyStatus(@Path("survey_id") surveyId: String): ApiEnvelope<SurveyStatusInfo>

    @GET("surveys/{survey_id}/summary")
    suspend fun getSurveySummary(@Path("survey_id") surveyId: String): ApiEnvelope<SurveySummary>

    @DELETE("surveys/{survey_id}")
    suspend fun deleteSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<MessageResponse>

    // Lifecycle transitions (frontend-integration.md §5).
    @POST("surveys/{survey_id}/start")
    suspend fun startSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    @POST("surveys/{survey_id}/complete")
    suspend fun completeSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    @POST("surveys/{survey_id}/submit")
    suspend fun submitSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    @POST("surveys/{survey_id}/reanalyze")
    suspend fun reanalyze(
        @Path("survey_id") surveyId: String,
        @Body body: ReanalyzeRequest,
    ): ApiEnvelope<ReanalyzeResult>

    @POST("surveys/{survey_id}/approve")
    suspend fun approveSurvey(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    @POST("surveys/{survey_id}/reject")
    suspend fun rejectSurvey(
        @Path("survey_id") surveyId: String,
        @Body body: RejectRequest,
    ): ApiEnvelope<Survey>

    @POST("surveys/{survey_id}/cancel")
    suspend fun cancelSurvey(
        @Path("survey_id") surveyId: String,
        @Body body: CancelRequest,
    ): ApiEnvelope<Survey>

    // ---- Survey request board (surveyor) ----
    @GET("survey-requests/available")
    suspend fun availableRequests(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ApiEnvelope<Page<Survey>>

    @GET("survey-requests/assigned")
    suspend fun assignedRequests(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ApiEnvelope<Page<Survey>>

    @POST("survey-requests/{survey_id}/accept")
    suspend fun acceptRequest(@Path("survey_id") surveyId: String): ApiEnvelope<Survey>

    // ---- Survey items ----
    @GET("surveys/{survey_id}/items")
    suspend fun getItems(@Path("survey_id") surveyId: String): ApiEnvelope<SurveyItemList>

    @POST("surveys/{survey_id}/items")
    suspend fun addItem(
        @Path("survey_id") surveyId: String,
        @Body body: SurveyItemCreate,
    ): ApiEnvelope<SurveyItem>

    @PATCH("survey-items/{item_id}")
    suspend fun updateItem(
        @Path("item_id") itemId: String,
        @Body body: SurveyItemUpdate,
    ): ApiEnvelope<SurveyItem>

    @DELETE("survey-items/{item_id}")
    suspend fun deleteItem(@Path("item_id") itemId: String): ApiEnvelope<MessageResponse>

    @POST("survey-items/merge")
    suspend fun mergeItems(@Body body: ItemMergeRequest): ApiEnvelope<SurveyItem>

    @POST("survey-items/{item_id}/split")
    suspend fun splitItem(
        @Path("item_id") itemId: String,
        @Body body: ItemSplitRequest,
    ): ApiEnvelope<SurveyItemList>

    // ---- Media ----
    @GET("surveys/{survey_id}/media")
    suspend fun listMedia(
        @Path("survey_id") surveyId: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): ApiEnvelope<Page<Media>>

    @GET("media/{media_id}")
    suspend fun getMedia(@Path("media_id") mediaId: String): ApiEnvelope<Media>

    @DELETE("media/{media_id}")
    suspend fun deleteMedia(@Path("media_id") mediaId: String): ApiEnvelope<MessageResponse>

    @Multipart
    @POST("surveys/{survey_id}/images")
    suspend fun uploadImages(
        @Path("survey_id") surveyId: String,
        @Part files: List<MultipartBody.Part>,
        @Part("room_location") roomLocation: RequestBody? = null,
    ): ApiEnvelope<MediaUploadResult>

    @Multipart
    @POST("surveys/{survey_id}/videos")
    suspend fun uploadVideos(
        @Path("survey_id") surveyId: String,
        @Part files: List<MultipartBody.Part>,
        @Part("room_location") roomLocation: RequestBody? = null,
    ): ApiEnvelope<MediaUploadResult>
}
