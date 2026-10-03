package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate

/** 대표 이미지 요청의 input(사용자 메시지) 생성. 시스템 지시는 resources/prompts/popup-image-instructions.txt. */
object ImagePrompt {
    private const val UNKNOWN = "미상"

    fun build(
        popup: Popup,
        today: LocalDate,
    ): String {
        val address = popup.addressRoad?.takeIf { it.isNotBlank() } ?: popup.addressJibun?.takeIf { it.isNotBlank() }
        val place = listOfNotNull(popup.placeName ?: popup.title, address?.let { "($it)" }).joinToString(" ")

        return listOf(
            "오늘 날짜: $today",
            "팝업 이름: ${popup.title}",
            "브랜드: ${popup.brand?.takeIf { it.isNotBlank() } ?: UNKNOWN}",
            "장소: $place",
            "기간: ${popup.startDate ?: UNKNOWN} ~ ${popup.endDate ?: UNKNOWN}",
        ).joinToString("\n")
    }
}
