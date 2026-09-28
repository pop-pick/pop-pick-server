package com.poppick.poppick.feature.planner.domain

import java.time.LocalDate

/**
 * 후보 팝업 검색 입력. 관심 카테고리 · 선호 활동은 memberKey 로 조회한다.
 * 동행 · 소요시간 · 시작시각은 검색 조건이 아니라 LLM 단계 입력이라 여기 없다.
 */
data class CandidateCondition(
    val memberKey: String,
    /** favorite_area id. popup.area_id 로 pre-filter. */
    val areaId: Int,
    val visitDate: LocalDate,
    /** 사용자 자유 입력. */
    val note: String?,
    /** 후보에서 뺄 팝업 id(재생성 시 이미 쓴 팝업 등). */
    val excludePopupIds: Set<Long> = emptySet(),
)
