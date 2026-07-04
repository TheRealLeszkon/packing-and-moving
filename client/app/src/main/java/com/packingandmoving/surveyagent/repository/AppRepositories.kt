package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.NetworkModule

/**
 * Manual dependency container for the repository layer. Keeps a single instance of each
 * repository wired to the shared [NetworkModule] stack. ViewModels obtain their
 * repositories from here (Phase 5) — no DI framework is used to keep wiring explicit.
 */
object AppRepositories {
    val auth: AuthRepository = AuthRepository(NetworkModule.api, NetworkModule.tokenProvider)
    val user: UserRepository = UserRepository(NetworkModule.api)
    val survey: SurveyRepository = SurveyRepository(NetworkModule.api)
    val item: ItemRepository = ItemRepository(NetworkModule.api)
    val media: MediaRepository = MediaRepository(NetworkModule.api)
}
