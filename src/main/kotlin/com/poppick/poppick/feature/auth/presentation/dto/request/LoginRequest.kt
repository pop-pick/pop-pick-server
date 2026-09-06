package com.poppick.poppick.feature.auth.presentation.dto.request

import com.poppick.poppick.feature.auth.domain.OAuthLogin
import com.poppick.poppick.feature.auth.domain.OAuthProvider

data class LoginRequest(
    val oAuthProvider: OAuthProvider,
    val authToken: String,
    val redirectUri: String,
) {
    fun toOAuthLogin() =
        OAuthLogin(
            oAuthProvider = oAuthProvider,
            authToken = authToken,
            redirectUri = redirectUri,
        )
}
