package com.poppick.poppick.feature.planner.presentation.dto.response

import com.poppick.poppick.feature.planner.domain.PlannerTabCounts
import io.swagger.v3.oas.annotations.media.Schema

/** "내 일정" 탭 라벨 건수. */
data class PlannerCountsResponse(
    @field:Schema(description = "다가오는 일정(SCHEDULED, 방문일 >= 오늘)", example = "3")
    val upcoming: Long,
    @field:Schema(description = "지난 일정(SCHEDULED, 방문일 < 오늘)", example = "2")
    val past: Long,
    @field:Schema(description = "취소된 일정(CANCELED)", example = "2")
    val canceled: Long,
) {
    companion object {
        fun from(counts: PlannerTabCounts) = PlannerCountsResponse(counts.upcoming, counts.past, counts.canceled)
    }
}
