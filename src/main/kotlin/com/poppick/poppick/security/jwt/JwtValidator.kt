package com.poppick.poppick.security.jwt

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.security.enums.TokenType
import io.jsonwebtoken.*
import io.jsonwebtoken.security.SecurityException
import org.springframework.stereotype.Component
import java.security.SignatureException
import java.time.Instant
import javax.crypto.SecretKey

const val BEARER = "Bearer "

@Component
class JwtValidator(
    private val secretKey: SecretKey,
) {
    fun getSubject(token: String) = getClaimsIfValid(token)
        .subject
        ?: throw AppException(ErrorType.INVALID_JWT)

    fun getJti(token: String) = getClaimsIfValid(token)
        .get(Claims.ID, String::class.java)
        ?: throw AppException(ErrorType.INVALID_JWT)

    fun getSid(token: String) = getClaimsIfValid(token)
        .get(SID_CLAIM, String::class.java)
        ?: throw AppException(ErrorType.INVALID_JWT)

    private fun getTokenType(token: String) = getClaimsIfValid(token)
        .get(TYPE_CLAIM, String::class.java)
        ?: throw AppException(ErrorType.INVALID_JWT)

    fun getExpiresAt(token: String): Instant = getClaimsIfValid(token).expiration.toInstant()

    fun getBearerTokenBody(token: String): String {
        if (!isBearerToken(token)) {
            throw AppException(ErrorType.INVALID_TOKEN_METHOD)
        }

        return token.removePrefix(BEARER)
    }

    fun validateTokenType(token: String, expectedType: TokenType) {
        if (getTokenType(token) != expectedType.name) {
            throw AppException(ErrorType.INVALID_TOKEN_TYPE)
        }
    }

    fun getClaimsIfValid(token: String): Claims = validate(token).payload

    private fun validate(token: String): Jws<Claims> =
        try {
            Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
        } catch (e: Exception) {
            throw when (e) {
                is MalformedJwtException -> AppException(ErrorType.MALFORMED_JWT)
                is UnsupportedJwtException -> AppException(ErrorType.UNSUPPORTED_JWT)
                is ExpiredJwtException -> AppException(ErrorType.EXPIRED_JWT)
                is SecurityException, is SignatureException -> AppException(ErrorType.INVALID_SIGNATURE)
                is IllegalArgumentException -> AppException(ErrorType.INVALID_JWT)
                else -> AppException(ErrorType.SERVER_ERROR)
            }
        }

    private fun isBearerToken(token: String) = token.startsWith(BEARER)
}