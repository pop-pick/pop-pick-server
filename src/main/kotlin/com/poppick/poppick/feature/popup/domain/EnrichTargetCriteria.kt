package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 보강 대상 조회 조건. 아래 셋의 합집합이며 시각은 전부 호출 측에서 계산해 넣는다.
 * 1. 미보강: enriched_at IS NULL
 * 2. 핵심 필드 공백 재시도: enrich_retry_count < retryLimit AND enriched_at < retryBefore
 *    AND (start_date · end_date · interest_category_id · area_id 중 NULL)
 * 3. 진행 중 갱신: end_date >= today 이고
 *    end_date <= imminentUntil 이면 enriched_at < imminentRefreshBefore, 그 외엔 enriched_at < refreshBefore.
 *    enrich_retry_count 는 보지 않는다. 종료됐거나 종료일이 없는 팝업은 대상이 아니다.
 */
data class EnrichTargetCriteria(
    val retryLimit: Int,
    val retryBefore: OffsetDateTime,
    val today: LocalDate,
    val refreshBefore: OffsetDateTime,
    val imminentUntil: LocalDate,
    val imminentRefreshBefore: OffsetDateTime,
    val limit: Int,
) {
    /** 1 · 2 에 해당하지 않고 3 으로만 고른 대상인지(요약 로그의 refreshed). */
    fun isRefresh(popup: Popup): Boolean {
        val enrichedAt = popup.enrichedAt ?: return false
        val retry =
            popup.enrichRetryCount < retryLimit &&
                enrichedAt < retryBefore &&
                (popup.startDate == null || popup.endDate == null || popup.interestCategoryId == null || popup.areaId == null)
        return !retry
    }
}

/** 보강 대상 1건. refresh 는 진행 중 갱신(3번) 사유로만 고른 대상. */
data class EnrichTarget(
    val popupId: Long,
    val refresh: Boolean,
)
