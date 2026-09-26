package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.domain.PerplexityEnrichResult
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupEnrichment
import com.poppick.poppick.feature.popup.domain.ReservationType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger { }

/**
 * 보강 결과를 기존 팝업에 병합한다(순수 함수, 로그 외 부수효과 없음). 상태는 두지 않으며 저장된 팝업은 전부 노출된다.
 * - found=false 또는 matches_place=false → retry+1, enrichedAt 만 갱신. 나머지 필드는 전부 유지.
 * - 그 외 → 필드 병합. 핵심 필드(기간 · 카테고리) 중 하나라도 비면 retry+1, 모두 채워지면 그대로.
 *   재보강 대상은 이 카운터와 핵심 필드 공백으로만 판단하므로(findEnrichTargets) retry 갱신을 빠뜨리지 않는다.
 * 장소 필드(placeId · placeName · address* · 좌표 · placeResolution) 와 source · externalId · rawPayload 는 절대 덮지 않는다.
 * 재보강은 결과 편차가 커서, 새 응답이 비어 있는 필드는 기존 값을 유지한다(reservationType 은 UNKNOWN 이면 유지).
 */
@Component
class PopupEnrichmentMerger {
    companion object {
        private const val MAX_TAGS = 10
        private const val MAX_SOURCE_URLS = 10

        /** end_date 가 12-31 이면서 기간이 이보다 길면 "연말까지" 류 플레이스홀더로 보고 버린다. */
        private const val YEAR_END_SUSPECT_DAYS = 90

        /** 기간이 이보다 길면 WARN 만 남긴다(뉴발란스 5/9~11/14 처럼 사실인 경우가 있어 값은 유지). */
        private const val LONG_PERIOD_SUSPECT_DAYS = 180
    }

    fun merge(
        popup: Popup,
        result: PerplexityEnrichResult,
        categories: Map<String, Int>,
        now: OffsetDateTime,
    ): Popup {
        val enrichment = result.enrichment

        if (enrichment.found && !enrichment.matchesPlace) {
            log.warn { "enrich: 장소 불일치 popupId=${popup.id} placeName=${popup.placeName} 응답 title=${enrichment.title}" }
        }
        if (!enrichment.describesPlace()) return popup.copy(enrichRetryCount = popup.enrichRetryCount + 1, enrichedAt = now)

        val (startDate, endDate) = mergePeriod(popup, enrichment)
        val interestCategoryId = enrichment.interestCategory?.let { categories[it] } ?: popup.interestCategoryId

        val merged =
            popup.copy(
                title = enrichment.title.nonBlank() ?: popup.title,
                brand = enrichment.brand.nonBlank() ?: popup.brand,
                description = enrichment.description.nonBlank() ?: popup.description,
                tags =
                    enrichment.tags
                        ?.mapNotNull { it.nonBlank() }
                        ?.distinct()
                        ?.take(MAX_TAGS)
                        ?.takeIf { it.isNotEmpty() } ?: popup.tags,
                interestCategoryId = interestCategoryId,
                startDate = startDate,
                endDate = endDate,
                openingHours = enrichment.openingHours.nonBlank() ?: popup.openingHours,
                reservationType = enrichment.reservationType.takeIf { it != ReservationType.UNKNOWN } ?: popup.reservationType,
                reservationUrl =
                    verifiedReservationUrl(popup.id, enrichment.reservationUrl, result.searchResultUrls) ?: popup.reservationUrl,
                reservationOpenAt = enrichment.reservationOpenAt.toOffsetDateTimeOrNull() ?: popup.reservationOpenAt,
                entryFee = enrichment.entryFee?.takeIf { it >= 0 } ?: popup.entryFee,
                sourceUrls = (popup.sourceUrls.orEmpty() + result.searchResultUrls.take(MAX_SOURCE_URLS)).distinct(),
                enrichedAt = now,
            )
        return if (merged.hasCoreFields()) merged else merged.copy(enrichRetryCount = popup.enrichRetryCount + 1)
    }

    /**
     * 기간 결정 순서
     * 1. 새 응답 파싱. 시작 > 종료면 둘 다 버린다.
     * 2. 새 종료일이 12-31 플레이스홀더면 버린다.
     * 3. 비어 있는 값은 기존 값으로 채운다. 섞은 결과가 역전되면 새 응답 값만 쓴다.
     * 4. 기존 값에서 온 12-31 플레이스홀더(이전 실행에서 저장된 값)도 버린다.
     * 5. 남은 기간이 180일을 넘으면 WARN 만 남긴다.
     */
    private fun mergePeriod(
        popup: Popup,
        enrichment: PopupEnrichment,
    ): Pair<LocalDate?, LocalDate?> {
        var newStart = enrichment.startDate.toLocalDateOrNull()
        var newEnd = enrichment.endDate.toLocalDateOrNull()
        if (newStart != null && newEnd != null && newStart > newEnd) {
            newStart = null
            newEnd = null
        }

        var placeholder: LocalDate? = null
        if (isYearEndPlaceholder(newStart ?: popup.startDate, newEnd)) {
            placeholder = newEnd
            newEnd = null
        }

        var startDate = newStart ?: popup.startDate
        var endDate = newEnd ?: popup.endDate
        if (startDate != null && endDate != null && startDate > endDate) {
            startDate = newStart
            endDate = newEnd
        }

        if (isYearEndPlaceholder(startDate, endDate)) {
            placeholder = endDate
            endDate = null
        }

        if (placeholder != null) log.warn { "enrich: end_date 12-31 의심 popupId=${popup.id} start=$startDate end=$placeholder" }

        // 12-31 처리 뒤에 남은 기간만 본다.
        if (startDate != null && endDate != null && ChronoUnit.DAYS.between(startDate, endDate) > LONG_PERIOD_SUSPECT_DAYS) {
            log.warn { "enrich: 장기 기간 의심 popupId=${popup.id} start=$startDate end=$endDate" }
        }
        return startDate to endDate
    }

    private fun isYearEndPlaceholder(
        startDate: LocalDate?,
        endDate: LocalDate?,
    ) = startDate != null &&
        endDate != null &&
        endDate.monthValue == 12 &&
        endDate.dayOfMonth == 31 &&
        ChronoUnit.DAYS.between(startDate, endDate) > YEAR_END_SUSPECT_DAYS

    // 모델이 지어낸 URL 을 막기 위해 검색 결과에 정확히 있거나 같은 host 의 URL 이 있을 때만 채택한다.
    private fun verifiedReservationUrl(
        popupId: Long?,
        reservationUrl: String?,
        searchResultUrls: List<String>,
    ): String? {
        val url = reservationUrl.nonBlank() ?: return null
        if (url in searchResultUrls) return url

        val host = url.host()
        val verified = url.takeIf { host != null && searchResultUrls.any { it.host() == host } }
        if (verified == null) log.debug { "reservation_url 검증 실패 popupId=$popupId url=$url" }
        return verified
    }

    private fun String.host() =
        runCatching { URI(trim()).host?.lowercase() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun String?.nonBlank() = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.toLocalDateOrNull() = nonBlank()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun String?.toOffsetDateTimeOrNull() = nonBlank()?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
}
