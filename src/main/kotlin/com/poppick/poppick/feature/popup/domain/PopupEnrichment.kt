package com.poppick.poppick.feature.popup.domain

/**
 * Perplexity 보강 응답(JSON 스키마 popup_enrichment)과 1:1.
 * JSON 은 snake_case 이며 클라이언트의 naming strategy 로 매핑한다.
 * 날짜 · 시각은 검증 전 원문 문자열 그대로 둔다(파싱은 병합 단계에서).
 */
data class PopupEnrichment(
    /** false 면 이 장소의 팝업 정보를 찾지 못함. 카카오 수집분은 팝업 여부를 묻지 않고 전부 팝업으로 본다. */
    val found: Boolean,
    /** false 면 응답 팝업이 장소명의 브랜드 · 행사와 다름(같은 건물의 다른 팝업 등). found=false 와 같이 처리한다. */
    val matchesPlace: Boolean,
    val title: String? = null,
    val brand: String? = null,
    /** interest_category.category 값 중 하나. */
    val interestCategory: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    /** YYYY-MM-DD */
    val startDate: String? = null,
    /** YYYY-MM-DD */
    val endDate: String? = null,
    val openingHours: Map<String, String>? = null,
    val reservationType: ReservationType = ReservationType.UNKNOWN,
    val reservationUrl: String? = null,
    /** YYYY-MM-DDTHH:mm:ss+09:00 */
    val reservationOpenAt: String? = null,
    val entryFee: Int? = null,
) {
    /** 응답이 이 장소의 팝업 정보를 담고 있는지. false 면 필드 값은 쓰지 않는다. */
    fun describesPlace() = found && matchesPlace
}
