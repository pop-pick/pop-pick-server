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
    private const val RETRY_REQUEST = "이전 검색에서 이 팝업의 정보를 일부 확인하지 못했다. 다른 검색어와 출처로 다시 찾아 JSON 으로 정리해줘."

    fun build(
        popup: Popup,
        today: LocalDate,
    ): String {
        val address = popup.addressRoad?.takeIf { it.isNotBlank() } ?: popup.addressJibun

        return listOfNotNull(
            "오늘 날짜: $today (KST)",
            "장소명: ${popup.placeName ?: popup.title}",
            "주소: ${address ?: UNKNOWN}",
            coordinates(popup)?.let { "좌표: $it" },
            kakaoPlaceUrl(popup)?.let { "참고: $it" },
            if (popup.enrichRetryCount > 0) RETRY_REQUEST else FIRST_REQUEST,
        ).joinToString("\n")
    }

    // 주소가 상권 정의 밖일 때 가장 가까운 상권 판단에 쓴다. 둘 중 하나라도 없으면 줄을 생략한다.
    private fun coordinates(popup: Popup): String? {
        val latitude = popup.latitude ?: return null
        val longitude = popup.longitude ?: return null
        return "위도 $latitude, 경도 $longitude"
    }

    // 수집 때 저장한 카카오 장소 페이지 URL. 없으면 place id 로 조립한다.
    private fun kakaoPlaceUrl(popup: Popup) =
        popup.sourceUrls?.firstOrNull { KAKAO_PLACE_HOST in it }
            ?: popup.placeId?.takeIf { it.isNotBlank() }?.let { "http://$KAKAO_PLACE_HOST/$it" }
}
