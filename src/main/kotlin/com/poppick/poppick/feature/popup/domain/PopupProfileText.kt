package com.poppick.poppick.feature.popup.domain

import java.security.MessageDigest
import java.util.HexFormat

/**
 * 팝업 프로필 임베딩 원문. 플래너가 사용자 요청의 취향 부분과 팝업을 비교해 후보를 고를 때 쓴다.
 * ```
 * {title}
 * 카테고리: {categoryName} · 브랜드: {brand}
 * {description}
 * 체험 · 키워드: {tags}
 * 지역: {구} {동}
 * ```
 * "무엇을 경험하는 곳인가" 만 담는다. 날짜 · 운영시간 · 예약 · 입장료 · 좌표 · 전체 주소는 SQL 필터나 플래너가 다룰 조건이라
 * 넣지 않는다(벡터 유사도의 잡음이 된다). 값이 없는 줄은 빼고, title 한 줄뿐이어도 그대로 쓴다.
 * 보강 스키마에 방문 가이드(동행 · 소요 시간) 필드가 추가되면 여기에 한 줄 추가할 예정.
 */
object PopupProfileText {
    private val WHITESPACE = Regex("\\s+")

    /** 동 이름 끝의 숫자 · "가"(성수동2가 → 성수동). */
    private val DONG_SUFFIX = Regex("\\d+가?$")

    fun build(
        popup: Popup,
        categoryName: String?,
    ): String {
        val categoryBrand =
            listOfNotNull(
                categoryName.clean()?.let { "카테고리: $it" },
                popup.brand.clean()?.let { "브랜드: $it" },
            ).joinToString(" · ")
        val tags =
            popup.tags
                .orEmpty()
                .mapNotNull { it.clean() }
                .joinToString(", ")

        return listOf(
            popup.title,
            categoryBrand,
            popup.description.orEmpty(),
            tags.prefixed("체험 · 키워드: "),
            region(popup).prefixed("지역: "),
        ).mapNotNull { it.clean() }
            .joinToString("\n")
    }

    /** SHA-256 hex(소문자 64자). */
    fun sha256(text: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    /** 지번("서울 성동구 성수동2가 273-13")의 구 + 동(성동구 성수동). 지번이 없으면 도로명의 구만. */
    private fun region(popup: Popup): String {
        popup.addressJibun.clean()?.split(" ")?.let { tokens ->
            val dong = tokens.getOrNull(2)?.replace(DONG_SUFFIX, "")?.takeIf { it.isNotEmpty() && !it.first().isDigit() }
            return listOfNotNull(tokens.getOrNull(1), dong).joinToString(" ")
        }
        return popup.addressRoad
            .clean()
            ?.split(" ")
            ?.getOrNull(1)
            .orEmpty()
    }

    /** 연속 공백(줄바꿈 포함)을 한 칸으로 · 앞뒤 공백 제거. 빈 값이면 NULL. */
    private fun String?.clean() = this?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.prefixed(prefix: String) = if (isBlank()) "" else prefix + this
}
