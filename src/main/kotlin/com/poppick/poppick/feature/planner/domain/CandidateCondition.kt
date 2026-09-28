package com.poppick.poppick.feature.planner.domain

import java.time.LocalDate

/**
 * 후보 팝업 검색 입력. 관심 카테고리 · 선호 활동은 요청에 담긴 이름을 그대로 쓴다(온보딩 값은 /form 기본값에만 쓴다).
 * 동행 · 소요시간 · 시작시각은 검색 조건이 아니라 LLM 단계 입력이라 여기 없다.
 */
data class CandidateCondition(
    /** favorite_area id. popup.area_id 로 pre-filter. */
    val areaId: Int,
    val visitDate: LocalDate,
    /** 관심 카테고리 이름(interest_category.category). */
    val categories: List<String>,
    /** 선호 활동 이름(preferred_activity.activity). */
    val activities: List<String>,
    /** 사용자 자유 입력. */
    val note: String?,
    /** 후보에서 뺄 팝업 id(이미 확정한 일정의 팝업 등). */
    val excludePopupIds: Set<Long> = emptySet(),
)
