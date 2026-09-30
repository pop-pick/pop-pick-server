package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.member.domain.AccompanyType
import java.time.LocalDate
import java.time.LocalTime

/** 코스 선정 입력. 지역 · 회원 취향은 후보 검색에서 이미 반영됐으므로 없다. */
data class CourseCondition(
    val visitDate: LocalDate,
    val startTime: LocalTime,
    val accompanyType: AccompanyType,
    val durationType: DurationType,
    /** 사용자 자유 입력. */
    val note: String?,
)
