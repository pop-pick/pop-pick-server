package com.poppick.poppick.feature.auth.service

import com.poppick.poppick.feature.auth.domain.Jwt
import com.poppick.poppick.feature.auth.implement.TokenManager
import org.springframework.stereotype.Service

@Service
class TokenService(
    private val tokenManager: TokenManager,
) {
    fun refresh(refreshToken: String): Jwt = tokenManager.reissue(refreshToken)

    fun logout(accessToken: String, refreshToken: String) = tokenManager.revoke(accessToken, refreshToken)
}
