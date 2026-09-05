package com.poppick.poppick.security.domain

import com.poppick.poppick.feature.auth.domain.OAuthMember
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GoogleUser(
    val sub: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String? = null,
    val picture: String? = null,
) {
    fun toOAuthMember() =
        OAuthMember.of(
            account = sub,
            provider = OAuthProvider.GOOGLE,
            email = email,
        )
}
