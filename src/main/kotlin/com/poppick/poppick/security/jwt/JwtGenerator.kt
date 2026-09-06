package com.poppick.poppick.security.jwt

import com.poppick.poppick.feature.auth.domain.Jwt
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.security.enums.TokenType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey
import kotlin.uuid.Uuid

const val TYPE_CLAIM = "type"
const val SID_CLAIM = "sid"
private const val ACCESS_TOKEN_EXPIRE_SECONDS = 60L * 60
private const val REFRESH_TOKEN_EXPIRE_SECONDS = 60L * 60 * 24 * 14

@Component
class JwtGenerator(
    private val secretKey: SecretKey,
) {
    fun generateJwt(memberKey: String): Jwt = issueJwt(memberKey, sid = Uuid.random().toString())

    fun regenerateJwt(
        memberKey: String,
        sid: String,
    ): Jwt = issueJwt(memberKey, sid)

    private fun issueJwt(
        memberKey: String,
        sid: String,
    ): Jwt {
        if (memberKey.isBlank()) {
            throw AppException(ErrorType.INVALID_MEMBER_KEY)
        }

        val refreshJti = Uuid.random().toString()
        val refreshExpiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRE_SECONDS)

        val accessToken = buildAccessToken(memberKey)
        val refreshToken = buildRefreshToken(memberKey, refreshExpiresAt, refreshJti, sid)

        return Jwt(
            accessToken = accessToken,
            refreshToken = refreshToken,
            sid = sid,
            refreshJti = refreshJti,
            refreshExpiresAt = refreshExpiresAt,
        )
    }

    private fun buildAccessToken(memberKey: String) =
        buildToken(
            memberKey = memberKey,
            tokenType = TokenType.ACCESS,
            expiresAt = Instant.now().plusSeconds(ACCESS_TOKEN_EXPIRE_SECONDS),
            jti = Uuid.random().toString(),
        )

    private fun buildRefreshToken(
        memberKey: String,
        refreshExpiresAt: Instant,
        refreshJti: String,
        sid: String,
    ) = buildToken(
        memberKey = memberKey,
        tokenType = TokenType.REFRESH,
        expiresAt = refreshExpiresAt,
        jti = refreshJti,
        sid = sid,
    )

    private fun buildToken(
        memberKey: String,
        tokenType: TokenType,
        expiresAt: Instant,
        jti: String,
        sid: String? = null,
    ): String {
        val builder =
            Jwts
                .builder()
                .subject(memberKey)
                .claim(TYPE_CLAIM, tokenType.name)
                .claim(Claims.ID, jti)
                .expiration(Date.from(expiresAt))

        sid?.let { builder.claim(SID_CLAIM, it) }

        return builder.signWith(secretKey).compact()
    }
}
