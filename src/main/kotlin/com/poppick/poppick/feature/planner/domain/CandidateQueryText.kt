package com.poppick.poppick.feature.planner.domain

/**
 * 후보 검색 쿼리 텍스트. PopupProfileText 와 같은 줄 형식을 써서 팝업 임베딩과 벡터 공간을 맞춘다.
 * ```
 * 카테고리: {관심 카테고리들}
 * 체험 · 키워드: {선호 활동들}
 * {자유 입력}
 * ```
 * 지역 · 동행 · 시간 · 날짜는 넣지 않는다. 지역은 SQL(area_id) 로 거르고, 나머지는 LLM 이 다룰 조건이라
 * 벡터에 섞으면 취향 유사도의 잡음이 된다. 값이 없는 줄은 빼고, 전부 비면 NULL(벡터 정렬 없이 검색).
 */
object CandidateQueryText {
    private const val MAX_NOTE_LENGTH = 200
    private val WHITESPACE = Regex("\\s+")

    fun build(
        categories: List<String>,
        activities: List<String>,
        note: String?,
    ): String? =
        listOfNotNull(
            categories.joined()?.let { "카테고리: $it" },
            activities.joined()?.let { "체험 · 키워드: $it" },
            note.clean()?.take(MAX_NOTE_LENGTH)?.trim(),
        ).takeIf { it.isNotEmpty() }
            ?.joinToString("\n")

    private fun List<String>.joined() = mapNotNull { it.clean() }.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /** 연속 공백(줄바꿈 포함)을 한 칸으로 · 앞뒤 공백 제거. 빈 값이면 NULL. */
    private fun String?.clean() = this?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }
}
