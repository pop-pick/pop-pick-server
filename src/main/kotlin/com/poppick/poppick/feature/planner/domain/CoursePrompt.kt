package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.popup.domain.Popup
import java.time.format.TextStyle
import java.util.Locale

/**
 * 코스 선정 요청의 input(사용자 메시지) 생성. 시스템 지시는 resources/prompts/planner-course-instructions.txt.
 * ```
 * ## 조건
 * - 방문일: 2026-10-03 (토)
 * - 시작 시각: 14:00
 * - 동행: 친구와
 * - 소요시간: 반나절 → 방문 개수 3~5곳, 총 소요 예산 300분
 * - 자유 입력: …
 *
 * ## 후보 팝업 (N건)
 * [popupId] 제목 | 카테고리: … · 브랜드: … | 구 동 | 위도,경도
 *   소개: …
 *   체험 · 키워드: …
 *   운영: 시작 ~ 종료 · 운영시간
 * ```
 * 정리 규칙(공백 · 길이)은 PopupProfileText 와 같지만 함수는 따로 둔다. 임베딩 원문과 프롬프트 표현이 서로 묶이지 않게.
 * 회원 식별자 · 이메일 같은 개인정보는 넣지 않는다.
 */
object CoursePrompt {
    private const val MAX_DESCRIPTION_LENGTH = 150
    private const val MAX_NOTE_LENGTH = 200
    private val WHITESPACE = Regex("\\s+")

    /**
     * @param candidates 검색 결과(유사도) 순서 그대로 렌더링한다.
     * @param categoryNames interest_category id → 이름. 없는 id 는 카테고리를 생략한다.
     */
    fun build(
        condition: CourseCondition,
        candidates: List<CandidatePopup>,
        categoryNames: Map<Int, String> = emptyMap(),
    ): String =
        listOf(
            conditionBlock(condition),
            candidateBlock(candidates, categoryNames),
        ).joinToString("\n\n")

    private fun conditionBlock(condition: CourseCondition): String {
        val duration = condition.durationType
        val stops =
            if (duration.minStops == duration.maxStops) "${duration.minStops}곳" else "${duration.minStops}~${duration.maxStops}곳"
        val dayOfWeek = condition.visitDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

        return listOfNotNull(
            "## 조건",
            "- 방문일: ${condition.visitDate} ($dayOfWeek)",
            "- 시작 시각: ${"%02d:%02d".format(condition.startTime.hour, condition.startTime.minute)}",
            "- 동행: ${condition.accompanyType.label}",
            "- 소요시간: ${duration.label} → 방문 개수 $stops, 총 소요 예산 ${duration.budgetMin}분",
            condition.note
                .clean()
                ?.take(MAX_NOTE_LENGTH)
                ?.trim()
                ?.let { "- 자유 입력: $it" },
        ).joinToString("\n")
    }

    private fun candidateBlock(
        candidates: List<CandidatePopup>,
        categoryNames: Map<Int, String>,
    ): String =
        (
            listOf("## 후보 팝업 (${candidates.size}건)", "앞쪽일수록 관심사와 더 가깝다.") +
                candidates.map { candidate(it.popup, categoryNames) }
        ).joinToString("\n")

    private fun candidate(
        popup: Popup,
        categoryNames: Map<Int, String>,
    ): String {
        val categoryBrand =
            listOfNotNull(
                popup.interestCategoryId
                    ?.let { categoryNames[it] }
                    .clean()
                    ?.let { "카테고리: $it" },
                popup.brand.clean()?.let { "브랜드: $it" },
            ).joinToString(" · ")
        val coordinates =
            if (popup.latitude != null && popup.longitude != null) {
                "%.4f,%.4f".format(Locale.ROOT, popup.latitude, popup.longitude)
            } else {
                null
            }
        val header =
            listOfNotNull(
                "[${popup.id}] ${popup.title.clean().orEmpty()}",
                categoryBrand.clean(),
                region(popup).clean(),
                coordinates,
            ).joinToString(" | ")

        val tags =
            popup.tags
                .orEmpty()
                .mapNotNull { it.clean() }
                .joinToString(", ")
        return listOfNotNull(
            header,
            popup.description
                .clean()
                ?.take(MAX_DESCRIPTION_LENGTH)
                ?.trim()
                ?.let { "  소개: $it" },
            tags.clean()?.let { "  체험 · 키워드: $it" },
            operation(popup)?.let { "  운영: $it" },
        ).joinToString("\n")
    }

    /** "2026-06-19 ~ 2026-11-01 · 매일 11:00~20:00". 기간 · 운영시간이 모두 없으면 NULL. */
    private fun operation(popup: Popup): String? {
        val period =
            if (popup.startDate == null && popup.endDate == null) {
                null
            } else {
                listOfNotNull(popup.startDate?.toString(), "~", popup.endDate?.toString()).joinToString(" ")
            }
        return listOfNotNull(period, popup.openingHours.clean()).joinToString(" · ").clean()
    }

    /** 지번("서울 성동구 성수동2가 273-13")의 구 + 동(성동구 성수동2가). 지번이 없으면 도로명의 구만. */
    private fun region(popup: Popup): String {
        popup.addressJibun.clean()?.split(" ")?.let { tokens ->
            val dong = tokens.getOrNull(2)?.takeIf { !it.first().isDigit() }
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
}
