package com.poppick.poppick.security.domain

import com.poppick.poppick.feature.auth.domain.OAuthMember
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDateTime

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoUser(
    val id: Long,
    val connectedAt: LocalDateTime,
    val kakaoAccount: KakaoAccount,
) {
    fun toOAuthMember() =
        OAuthMember.of(
            account = id.toString(),
            provider = OAuthProvider.KAKAO,
            email = kakaoAccount.email,
        )
}
