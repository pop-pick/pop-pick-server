package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.SourceType
import java.time.LocalDate
import java.time.OffsetDateTime

interface CustomPopupRepository {
    fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupEntity?

    /**
     * 보강 대상(id 오름차순, 최대 limit 건). 상태가 아니라 데이터로 판단한다.
     * - 미보강: enriched_at IS NULL
     * - 재보강: enrich_retry_count < retryLimit AND enriched_at < retryBefore
     *   AND (start_date IS NULL OR end_date IS NULL OR interest_category_id IS NULL OR area_id IS NULL)
     * area_id 는 재보강 트리거일 뿐 핵심 필드(Popup.hasCoreFields)가 아니다. 상권 밖 팝업은 NULL 이 정상이라
     * retryLimit 까지만 재시도되고 멈추며, INCOMPLETE 집계에도 들어가지 않는다.
     */
    fun findEnrichTargets(
        retryLimit: Int,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<PopupEntity>

    /**
     * 임베딩 대상(id 오름차순): 종료가 확정된 팝업(end_date < today)만 뺀 전부. 카테고리 · 기간 유무는 보지 않는다.
     * end_date IS NULL OR end_date >= today
     */
    fun findEmbedTargets(today: LocalDate): List<PopupEntity>
}
