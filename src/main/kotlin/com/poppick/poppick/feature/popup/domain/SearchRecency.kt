package com.poppick.poppick.feature.popup.domain

/**
 * 보강 웹 검색의 최신성 필터. 첫 시도는 YEAR, 재시도는 필터 없음(NONE)으로 넓힌다.
 * 카카오 등록 팝업은 2~3개월 전 것도 많아 month 는 첫 시도에서 재현율을 깎는다.
 */
enum class SearchRecency(
    /** search_recency_filter 값. NULL 이면 요청에서 키 자체를 뺀다. */
    val value: String?,
) {
    YEAR("year"),
    NONE(null),
    ;

    companion object {
        fun of(popup: Popup) = if (popup.enrichRetryCount > 0) NONE else YEAR
    }
}
