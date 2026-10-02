package com.poppick.poppick.feature.popupdetail.dataaccess

import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

private val log = KotlinLogging.logger { }

private const val VIEW_MARK_KEY_PREFIX = "popup:view:"

/** 같은 조회자의 재조회를 조회수에서 빼는 기간. */
private val VIEW_MARK_TTL: Duration = Duration.ofMinutes(10)

/**
 * "이 조회자가 최근 이 팝업을 봤다" 메모(Redis). 조회수 자체는 저장하지 않는다.
 * key: popup:view:{popupId}:{m:memberKey | a:sha256}, value: "1", TTL 10분.
 */
@Component
class PopupViewMarkStore(
    private val redisTemplate: StringRedisTemplate,
) {
    /**
     * 메모가 없을 때만 TTL 과 함께 남긴다(SET NX EX, 원자적). 새로 남겼으면 true, 이미 있으면 false.
     * Redis 오류면 판단할 수 없으므로 false 로 보고 WARN 을 남긴다(예외를 전파하지 않는다).
     */
    fun markIfAbsent(
        popupId: Long,
        viewer: PopupViewer,
    ): Boolean =
        try {
            redisTemplate.opsForValue().setIfAbsent(key(popupId, viewer), "1", VIEW_MARK_TTL) == true
        } catch (e: DataAccessException) {
            log.warn { "view: 조회 메모 기록 실패 popupId=$popupId ${e.javaClass.simpleName}: ${e.message}" }
            false
        }

    private fun key(
        popupId: Long,
        viewer: PopupViewer,
    ) = "$VIEW_MARK_KEY_PREFIX$popupId:${viewer.key}"
}
