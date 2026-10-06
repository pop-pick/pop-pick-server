package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.EnrichTargetCriteria
import com.poppick.poppick.feature.popup.domain.SourceType
import java.time.LocalDate
import java.time.OffsetDateTime

interface CustomPopupRepository {
    fun findBySourceAndExternalId(
        source: SourceType,
        externalId: String,
    ): PopupEntity?

    /**
     * 보강 대상(최대 criteria.limit 건). 상태가 아니라 데이터로 판단한다. 조건은 EnrichTargetCriteria 참고.
     * 정렬은 enriched_at ASC NULLS FIRST, popup_id ASC 라 미보강 팝업이 limit 안에서 항상 먼저 처리된다.
     * area_id 는 재보강 트리거일 뿐 핵심 필드(Popup.hasCoreFields)가 아니다. 보강 응답이 항상 상권을 채우므로
     * 보통은 첫 보강에서 채워지고, 매핑에 실패하면 retryLimit 까지만 재시도되고 멈추며 INCOMPLETE 집계에도 들어가지 않는다.
     */
    fun findEnrichTargets(criteria: EnrichTargetCriteria): List<PopupEntity>

    /**
     * 이미지 수집 대상(id 오름차순, 최대 limit 건).
     * image_urls 가 NULL 이거나 빈 배열 AND enriched_at IS NOT NULL AND (brand IS NOT NULL OR description IS NOT NULL)
     * AND (end_date IS NULL OR end_date >= today) AND (image_checked_at IS NULL OR image_checked_at < retryBefore)
     */
    fun findImageTargets(
        today: LocalDate,
        retryBefore: OffsetDateTime,
        limit: Int,
    ): List<PopupEntity>

    /**
     * 임베딩 대상(id 오름차순): 종료가 확정된 팝업(end_date < today)만 뺀 전부. 카테고리 · 기간 유무는 보지 않는다.
     * end_date IS NULL OR end_date >= today
     */
    fun findEmbedTargets(today: LocalDate): List<PopupEntity>
}
