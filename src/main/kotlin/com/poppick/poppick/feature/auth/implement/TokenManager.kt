package com.poppick.poppick.feature.auth.implement

import com.poppick.poppick.feature.auth.domain.Jwt
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.security.enums.TokenType
import com.poppick.poppick.security.jwt.JwtGenerator
import com.poppick.poppick.security.jwt.JwtValidator
import com.poppick.poppick.security.session.AccessTokenBlacklist
import com.poppick.poppick.security.session.RefreshSessionStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TokenManager(
    private val jwtGenerator: JwtGenerator,
    private val jwtValidator: JwtValidator,
    private val refreshSessionStore: RefreshSessionStore,
    private val accessTokenBlacklist: AccessTokenBlacklist,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun issue(memberKey: String): Jwt =
        jwtGenerator.generateJwt(memberKey).also {
            refreshSessionStore.save(it.sid, it.refreshJti, it.refreshExpiresAt)
        }

    fun reissue(refreshToken: String): Jwt {
        jwtValidator.validateTokenType(refreshToken, TokenType.REFRESH)
        val memberKey = jwtValidator.getSubject(refreshToken)
        val sid = jwtValidator.getSid(refreshToken)
        val jti = jwtValidator.getJti(refreshToken)
        validateJwtId(sid, jti)


        return jwtGenerator.regenerateJwt(memberKey, sid).also {
            refreshSessionStore.save(it.sid, it.refreshJti, it.refreshExpiresAt)
        }
    }

    private fun validateJwtId(sid: String, jti: String) {
        if (refreshSessionStore.findCurrentJti(sid) != jti) {
            refreshSessionStore.revoke(sid)
            throw AppException(ErrorType.INVALID_REFRESH_SESSION)
        }
    }

    fun revoke(accessToken: String, refreshToken: String) {
        runCatching {
            val tokenBody = jwtValidator.getBearerTokenBody(accessToken)
            jwtValidator.validateTokenType(tokenBody, TokenType.ACCESS)
            accessTokenBlacklist.add(jwtValidator.getJti(tokenBody), jwtValidator.getExpiresAt(tokenBody))
        }.onFailure { logger.warn("${ErrorType.FAILED_REVOKE_ACCESS.message} ${it.message}") }

        runCatching {
            jwtValidator.validateTokenType(refreshToken, TokenType.REFRESH)
            refreshSessionStore.revoke(jwtValidator.getSid(refreshToken))
        }.onFailure { logger.warn("${ErrorType.FAILED_REVOKE_ACCESS.message} ${it.message}") }
    }
}
