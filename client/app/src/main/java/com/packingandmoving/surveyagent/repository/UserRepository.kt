package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.User
import com.packingandmoving.surveyagent.model.UserUpdate

/** Current user's profile (GET/PUT /users/me). */
class UserRepository(private val api: SurveyAgentApi) {
    suspend fun getProfile(): ApiResult<User> = safeApiCall { api.getMe() }

    suspend fun updateName(name: String?): ApiResult<User> =
        safeApiCall { api.updateMe(UserUpdate(name = name)) }
}
