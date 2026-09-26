package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate

/**
 * 보강 요청의 input(사용자 메시지) 생성. 시스템 지시는 resources/prompts/popup-enrich-instructions.txt.
 * 브랜드 분리 · 재검색은 모델이 지시문에 따라 스스로 한다. 카카오 분류는 장소 성격을 오해하게 만들어 넣지 않는다.
 */
object EnrichmentPrompt {
    private const val UNKNOWN = "알 수 없음"
    private const val KAKAO_PLACE_HOST = "place.map.kakao.com"
    private const val FIRST_REQUEST = "이 팝업스토어의 정보를 웹에서 찾아 JSON 으로 정리해줘."
    private const val RETRY_REQUEST = "이전 검색에서 이 팝업의 기간을 확인하지 못했다. 다른 검색어와 출처로 다시 찾아 JSON 으로 정리해줘."

    fun build(
        popup: Popup,
        today: LocalDate,
    ): String {
        val address = popup.addressRoad?.takeIf { it.isNotBlank() } ?: popup.addressJibun

        return listOfNotNull(
            "오늘 날짜: $today (KST)",
            "장소명: ${popup.placeName ?: popup.title}",
            "주소: ${address ?: UNKNOWN}",
            kakaoPlaceUrl(popup)?.let { "참고: $it" },
            if (popup.enrichRetryCount > 0) RETRY_REQUEST else FIRST_REQUEST,
        ).joinToString("\n")
    }

    // 수집 때 저장한 카카오 장소 페이지 URL. 없으면 place id 로 조립한다.
    private fun kakaoPlaceUrl(popup: Popup) =
        popup.sourceUrls?.firstOrNull { KAKAO_PLACE_HOST in it }
            ?: popup.placeId?.takeIf { it.isNotBlank() }?.let { "http://$KAKAO_PLACE_HOST/$it" }
}
