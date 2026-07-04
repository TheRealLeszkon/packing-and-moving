package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.RoleUpdate
import com.packingandmoving.surveyagent.model.User
import com.packingandmoving.surveyagent.model.UserRole
import com.packingandmoving.surveyagent.model.UserUpdate

/** Current user's profile (GET/PUT /users/me) and demo role switch. */
class UserRepository(private val api: SurveyAgentApi) {
    suspend fun getProfile(): ApiResult<User> = safeApiCall { api.getMe() }

    suspend fun updateName(name: String?): ApiResult<User> =
        safeApiCall { api.updateMe(UserUpdate(name = name)) }

    /** Demo-only: switch the current user's backend role so RBAC-gated flows work. */
    suspend fun setRole(role: UserRole): ApiResult<User> =
        safeApiCall { api.setMyRole(RoleUpdate(role)) }
}
