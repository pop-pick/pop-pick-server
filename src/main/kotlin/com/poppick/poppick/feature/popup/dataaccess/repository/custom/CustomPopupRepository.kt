package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.SourceType
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
}
