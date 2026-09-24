package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate

/**
 * 보강 요청의 input(사용자 메시지) 생성. 시스템 지시는 resources/prompts/popup-enrich-instructions.txt.
 * 브랜드 분리 · 재검색은 모델이 지시문에 따라 스스로 한다. 카카오 분류는 장소 성격을 오해하게 만들어 넣지 않는다.
 */
object EnrichmentPrompt {
    private const val UNKNOWN = "알 수 없음"

    fun build(
        popup: Popup,
        today: LocalDate,
    ): String {
        val address = popup.addressRoad?.takeIf { it.isNotBlank() } ?: popup.addressJibun

        return listOf(
            "오늘 날짜: $today (KST)",
            "장소명: ${popup.placeName ?: popup.title}",
            "주소: ${address ?: UNKNOWN}",
            "이 팝업스토어의 정보를 웹에서 찾아 JSON 으로 정리해줘.",
        ).joinToString("\n")
    }
}
