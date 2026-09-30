package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.member.domain.AccompanyType
import java.time.LocalDate
import java.time.LocalTime

/** 코스 생성 요청. 관심 카테고리 · 선호 활동은 이 요청에만 쓰고 온보딩 값은 바꾸지 않는다. */
data class PlannerGenerateCommand(
    val areaId: Int,
    val visitDate: LocalDate,
    val startTime: LocalTime,
    val accompanyType: AccompanyType,
    val durationType: DurationType,
    val interestCategoryIds: List<Int>,
    val preferredActivityIds: List<Int>,
    val note: String?,
)
