package com.poppick.poppick.feature.planner.presentation.dto.response

import io.swagger.v3.oas.annotations.media.Schema

data class PlannerCalendarResponse(
    @field:Schema(description = "구글 캘린더 \"일정 추가\" 링크", example = "https://calendar.google.com/calendar/render?action=TEMPLATE&text=...")
    val googleCalendarUrl: String,
    @field:Schema(description = ".ics 다운로드 경로(서버 상대 경로, 인증 필요)", example = "/api/v1/planners/12/calendar.ics")
    val icsUrl: String,
)
