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
     *   AND (start_date IS NULL OR end_date IS NULL OR interest_category_id IS NULL)
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

    /** 삭제 대상 id(오름차순): end_date IS NULL OR end_date < today */
    fun findExpiredPopupIds(today: LocalDate): List<Long>

    /** JPQL bulk delete(영속성 컨텍스트를 거치지 않는다). 삭제 건수를 반환한다. */
    fun deleteByIds(ids: List<Long>): Int
}
