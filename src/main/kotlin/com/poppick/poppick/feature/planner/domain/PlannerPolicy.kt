package com.poppick.poppick.feature.planner.domain

import java.time.LocalTime

/** 플래너 생성 입력 범위. /form 의 선택 범위와 /generate 검증이 같은 값을 쓴다. */
object PlannerPolicy {
    /** 방문일은 오늘부터 이 일수 이내. */
    const val MAX_DAYS_AHEAD = 30L

    val START_TIME_MIN: LocalTime = LocalTime.of(8, 0)
    val START_TIME_MAX: LocalTime = LocalTime.of(20, 0)

    /** 오늘 방문이면 시작 시각은 현재 시각에서 이만큼 뒤부터. */
    const val TODAY_LEAD_MINUTES = 30L
}
