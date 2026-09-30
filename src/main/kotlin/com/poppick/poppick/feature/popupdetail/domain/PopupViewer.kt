package com.poppick.poppick.feature.popupdetail.domain

import java.security.MessageDigest

/**
 * 상세 조회수 중복 판정에 쓰는 조회자 식별값.
 * 회원은 memberKey, 비회원은 IP + "|" + User-Agent 의 SHA-256(hex) 를 쓰며, 원본 IP · User-Agent 는 담지 않는다.
 */
@ConsistentCopyVisibility
data class PopupViewer private constructor(
    /** 조회 메모 key 의 조회자 구간. 회원 "m:{memberKey}", 비회원 "a:{sha256 hex}". */
    val key: String,
) {
    companion object {
        private const val MEMBER_PREFIX = "m:"
        private const val ANONYMOUS_PREFIX = "a:"

        fun member(memberKey: String): PopupViewer {
            require(memberKey.isNotBlank()) { "memberKey 가 비어 있다" }
            return PopupViewer("$MEMBER_PREFIX$memberKey")
        }

        /**
         * 비회원 조회자. IP 는 X-Real-IP → X-Forwarded-For 맨 오른쪽 값 → remoteAddr 순으로 정한다.
         * X-Forwarded-For 의 왼쪽 값은 클라이언트가 임의로 넣을 수 있어 쓰지 않는다(맨 오른쪽은 프록시가 붙인 값).
         */
        fun anonymous(
            xRealIp: String?,
            xForwardedFor: String?,
            remoteAddr: String,
            userAgent: String?,
        ): PopupViewer {
            val ip = clientIp(xRealIp, xForwardedFor, remoteAddr)
            return PopupViewer("$ANONYMOUS_PREFIX${sha256Hex("$ip|${userAgent.orEmpty()}")}")
        }

        internal fun clientIp(
            xRealIp: String?,
            xForwardedFor: String?,
            remoteAddr: String,
        ): String =
            xRealIp?.trim()?.takeIf { it.isNotEmpty() }
                ?: xForwardedFor
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.lastOrNull { it.isNotEmpty() }
                ?: remoteAddr

        private fun sha256Hex(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
