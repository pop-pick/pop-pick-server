package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerForm
import com.poppick.poppick.feature.planner.domain.PlannerPolicy
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime

data class PlannerFormResponse(
    @field:Schema(description = "입력 기본값(온보딩 값). 온보딩 전이면 null · 빈 배열")
    val defaults: Defaults,
    @field:Schema(description = "선택지")
    val options: Options,
    @field:Schema(description = "방문일 선택 범위(오늘~30일 뒤)")
    val visitDateRange: DateRange,
    @field:Schema(description = "시작 시각 선택 범위")
    val startTimeRange: TimeRange,
) {
    data class Defaults(
        @field:Schema(description = "기본 지역 id. 온보딩 전이면 NULL", example = "1")
        val areaId: Int?,
        @field:Schema(description = "기본 관심 카테고리 id", example = "[1, 5]")
        val interestCategoryIds: List<Int>,
        @field:Schema(description = "기본 선호 활동 id", example = "[2]")
        val preferredActivityIds: List<Int>,
    )

    data class Options(
        @field:Schema(description = "지역")
        val areas: List<IdName>,
        @field:Schema(description = "관심 카테고리")
        val interestCategories: List<IdName>,
        @field:Schema(description = "선호 활동")
        val preferredActivities: List<IdName>,
        @field:Schema(description = "동행 유형(code = AccompanyType)")
        val accompanyTypes: List<CodeLabel>,
        @field:Schema(description = "소요시간 유형(code = DurationType)")
        val durationTypes: List<CodeLabel>,
    )

    data class IdName(
        @field:Schema(description = "id", example = "1")
        val id: Int,
        @field:Schema(description = "이름", example = "성수")
        val name: String,
    )

    data class CodeLabel(
        @field:Schema(description = "요청에 넣는 코드", example = "HALF_DAY")
        val code: String,
        @field:Schema(description = "표시 이름", example = "반나절")
        val label: String,
        @field:Schema(description = "부가 설명. durationTypes 만 채운다", example = "팝업 3~5곳, 약 5시간")
        val description: String? = null,
    )

    data class DateRange(
        @field:Schema(description = "최소", example = "2026-09-28")
        val min: LocalDate,
        @field:Schema(description = "최대", example = "2026-10-28")
        val max: LocalDate,
    )

    data class TimeRange(
        @field:JsonFormat(pattern = "HH:mm")
        @field:Schema(description = "최소 HH:mm", example = "08:00")
        val min: LocalTime,
        @field:JsonFormat(pattern = "HH:mm")
        @field:Schema(description = "최대 HH:mm", example = "20:00")
        val max: LocalTime,
    )

    companion object {
        fun from(form: PlannerForm) =
            PlannerFormResponse(
                defaults = Defaults(form.defaultAreaId, form.defaultInterestCategoryIds, form.defaultPreferredActivityIds),
                options =
                    Options(
                        areas = form.areas.map { IdName(it.id, it.area) },
                        interestCategories = form.interestCategories.map { IdName(it.id, it.category) },
                        preferredActivities = form.preferredActivities.map { IdName(it.id, it.activity) },
                        accompanyTypes = AccompanyType.entries.map { CodeLabel(it.name, it.label) },
                        durationTypes = DurationType.entries.map { CodeLabel(it.name, it.label, describe(it)) },
                    ),
                visitDateRange = DateRange(form.visitDateMin, form.visitDateMax),
                startTimeRange = TimeRange(PlannerPolicy.START_TIME_MIN, PlannerPolicy.START_TIME_MAX),
            )

        /** "팝업 2곳, 약 3시간" · "팝업 3~5곳, 약 5시간" */
        private fun describe(type: DurationType): String {
            val stops = if (type.minStops == type.maxStops) "${type.minStops}" else "${type.minStops}~${type.maxStops}"
            return "팝업 ${stops}곳, 약 ${type.budgetMin / 60}시간"
        }
    }
}
