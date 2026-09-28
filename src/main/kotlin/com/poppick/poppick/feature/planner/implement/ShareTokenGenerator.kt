package com.poppick.poppick.feature.planner.implement

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/** 공유 토큰: SecureRandom 16바이트 → Base64 URL-safe, 패딩 없음(22자). */
@Component
class ShareTokenGenerator {
    companion object {
        private const val TOKEN_BYTES = 16
    }

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String = encoder.encodeToString(ByteArray(TOKEN_BYTES).also { random.nextBytes(it) })
}
