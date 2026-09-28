package com.poppick.poppick.feature.planner.presentation.dto.response

data class PlannerCalendarResponse(
    /** 구글 캘린더 "일정 추가" 링크. */
    val googleCalendarUrl: String,
    /** .ics 다운로드 경로(서버 상대 경로). */
    val icsUrl: String,
)
