package com.poppick.poppick.feature.planner.domain

/** "내 일정" 탭. DRAFT 는 어느 탭에도 나오지 않는다. */
enum class PlannerListTab {
    /** SCHEDULED, visit_date >= 오늘. visit_date · start_time · id 오름차순. */
    UPCOMING,

    /** SCHEDULED, visit_date < 오늘. visit_date · start_time · id 내림차순. */
    PAST,

    /** CANCELED. canceled_at · id 내림차순. */
    CANCELED,
}
