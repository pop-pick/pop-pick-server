package com.poppick.poppick.security.session

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private const val BLACKLIST_KEY_PREFIX = "blacklist:access:"

@Component
class AccessTokenBlacklist(
    private val redisTemplate: StringRedisTemplate,
) {
    fun add(
        jti: String,
        expiresAt: Instant,
    ) {
        val ttl = Duration.between(Instant.now(), expiresAt)
        if (ttl.isZero || ttl.isNegative) return

        redisTemplate.opsForValue().set(key(jti), "", ttl)
    }

    fun contains(jti: String): Boolean = redisTemplate.hasKey(key(jti)) ?: false

    private fun key(jti: String) = "$BLACKLIST_KEY_PREFIX$jti"
}
