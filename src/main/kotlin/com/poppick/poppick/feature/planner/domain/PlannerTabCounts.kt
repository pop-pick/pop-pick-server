package com.poppick.poppick.feature.planner.domain

/** "내 일정" 탭별 건수. 조건은 PlannerListTab 목록과 같고 DRAFT 는 세지 않는다. */
data class PlannerTabCounts(
    val upcoming: Long,
    val past: Long,
    val canceled: Long,
)
