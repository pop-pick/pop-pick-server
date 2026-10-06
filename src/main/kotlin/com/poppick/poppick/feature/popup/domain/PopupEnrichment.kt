package com.poppick.poppick.feature.popup.domain

/**
 * Perplexity 보강 응답(JSON 스키마 popup_enrichment)과 1:1.
 * JSON 은 snake_case 이며 클라이언트의 naming strategy 로 매핑한다.
 * 날짜 · 시각은 검증 전 원문 문자열 그대로 둔다(파싱은 병합 단계에서).
 */
data class PopupEnrichment(
    /**
     * 이 장소에 대해 검색으로 확인한 값이 하나라도 있으면 true(area · interestCategory 는 판단에서 뺀다). 카카오 수집분은 팝업 여부를 묻지 않고 전부 팝업으로 본다.
     * false 여도 값이 있는 필드는 병합한다(부분 정보 보존).
     */
    val found: Boolean,
    /** false 면 응답 팝업이 장소명의 브랜드 · 행사와 다름(같은 건물의 다른 팝업 등). 오귀속이라 area · interestCategory 외의 값은 버린다. */
    val matchesPlace: Boolean,
    val title: String? = null,
    val brand: String? = null,
    /**
     * interest_category.category 값 중 하나. 스키마상 필수이며 found · matches_place 와 무관하게 채워진다
     * (팝업 정보를 못 찾으면 장소 업종 기준, 근거가 없으면 "기타"). 모델이 어겨도 역직렬화가 깨지지 않게 nullable 로 둔다.
     */
    val interestCategory: String? = null,
    /**
     * favorite_area.area 값 중 하나. 상권 밖이면 가장 가까운 상권. 스키마상 필수이며 found · matches_place 와 무관하게 주소 기준으로 채워진다.
     * 모델이 어겨도 역직렬화가 깨지지 않게 nullable 로 둔다(병합 단계에서 WARN).
     */
    val area: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    /** YYYY-MM-DD */
    val startDate: String? = null,
    /** YYYY-MM-DD */
    val endDate: String? = null,
    /** 운영시간 · 휴무 한 줄. 예: "매일 11:00~20:00, 월 휴무" */
    val openingHours: String? = null,
    val reservationType: ReservationType = ReservationType.UNKNOWN,
    val reservationUrl: String? = null,
    /** YYYY-MM-DDTHH:mm:ss+09:00 */
    val reservationOpenAt: String? = null,
    val entryFee: Int? = null,
) {
    /** 응답이 이 장소의 팝업 정보를 담고 있는지(요약 집계의 enriched · notFound, 출처 URL 저장 여부). */
    fun describesPlace() = found && matchesPlace
}
