package com.poppick.poppick.feature.popup.domain

/** 보강 웹 검색의 최신성 필터. 첫 보강은 MONTH, 재시도는 YEAR 로 넓힌다. */
enum class SearchRecency(
    val value: String,
) {
    MONTH("month"),
    YEAR("year"),
    ;

    companion object {
        fun of(popup: Popup) = if (popup.enrichRetryCount > 0) YEAR else MONTH
    }
}
