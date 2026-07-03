package com.packingandmoving.surveyor.api

import com.packingandmoving.surveyor.model.ProcessingResponse
import com.packingandmoving.surveyor.model.ProcessingSummary
import com.packingandmoving.surveyor.model.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Retrofit definition of the FastAPI backend contract. Do not invent endpoints here. */
interface SurveyApi {

    @Multipart
    @POST("upload")
    suspend fun uploadImages(@Part files: List<MultipartBody.Part>): UploadResponse

    @GET("processing/{sessionId}")
    suspend fun getProcessing(@Path("sessionId") sessionId: String): ProcessingResponse

    @GET("processing")
    suspend fun listProcessing(): List<ProcessingSummary>
}
