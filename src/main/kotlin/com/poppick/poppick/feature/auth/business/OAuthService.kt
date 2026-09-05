package com.poppick.poppick.feature.auth.business

import com.poppick.poppick.feature.auth.domain.Jwt
import com.poppick.poppick.feature.auth.domain.OAuthLogin
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import com.poppick.poppick.feature.auth.implement.OAuthAuthenticator
import com.poppick.poppick.feature.auth.implement.OAuthRegistrar
import com.poppick.poppick.feature.auth.implement.TokenManager
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.stereotype.Service

@Service
class OAuthService(
    authenticators: List<OAuthAuthenticator>,
    private val oAuthRegistrar: OAuthRegistrar,
    private val tokenManager: TokenManager,
) {
    private val oAuthAuthenticatorMap: Map<OAuthProvider, OAuthAuthenticator> =
        authenticators.associateBy { it.provider }

    fun login(oAuthLogin: OAuthLogin): Jwt {
        val oAuthMember = oAuthAuthenticatorMap[oAuthLogin.oAuthProvider]?.authenticate(oAuthLogin)
            ?: throw AppException(ErrorType.UNSUPPORTED_PROVIDER)

        val memberKey = oAuthRegistrar.registerIfNewAndGetMemberKey(oAuthMember)

        return tokenManager.issue(memberKey)
    }
}
