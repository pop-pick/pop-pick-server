package com.poppick.poppick.feature.popupdetail.implement

import com.poppick.poppick.feature.popup.implement.PopupViewCountWriter
import com.poppick.poppick.feature.popupdetail.dataaccess.PopupViewMarkStore
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger { }

/**
 * 상세 조회 1건을 조회수에 반영한다. 최근 조회 메모를 먼저 남기고(Redis SET NX), 새로 남긴 경우에만 DB 조회수를 올린다.
 * 조회수는 부가 기능이라 Redis · DB 오류는 WARN 만 남기고 예외를 전파하지 않는다(상세 조회는 그대로 응답한다).
 */
@Component
class PopupViewCounter(
    private val popupViewMarkStore: PopupViewMarkStore,
    private val popupViewCountWriter: PopupViewCountWriter,
) {
    /** 올린 뒤 조회수를 반환한다. 중복 조회 · Redis 오류 · DB 오류 · 팝업 없음이면 NULL(호출 측은 기존 값을 쓴다). */
    fun count(
        popupId: Long,
        viewer: PopupViewer,
    ): Long? {
        val marked =
            runCatching { popupViewMarkStore.markIfAbsent(popupId, viewer) }
                .onFailure { log.warn { "view: 중복 판정 실패, 증가 건너뜀 popupId=$popupId ${it.javaClass.simpleName}: ${it.message}" } }
                .getOrDefault(false)
        if (!marked) return null

        return runCatching { popupViewCountWriter.increase(popupId) }
            .onFailure { log.warn { "view: 조회수 증가 실패 popupId=$popupId ${it.javaClass.simpleName}: ${it.message}" } }
            .getOrNull()
    }
}
