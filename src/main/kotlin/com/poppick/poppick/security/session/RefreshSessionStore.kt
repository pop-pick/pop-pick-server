package com.poppick.poppick.security.session

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private const val SESSION_KEY_PREFIX = "session:"

@Component
class RefreshSessionStore(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(sid: String, jti: String, expiresAt: Instant) {
        val ttl = Duration.between(Instant.now(), expiresAt)
        if (ttl.isZero || ttl.isNegative) return

        redisTemplate.opsForValue().set(key(sid), jti, ttl)
    }

    fun findCurrentJti(sid: String): String? = redisTemplate.opsForValue().get(key(sid))

    fun revoke(sid: String) {
        redisTemplate.delete(key(sid))
    }

    private fun key(sid: String) = "$SESSION_KEY_PREFIX$sid"
}
