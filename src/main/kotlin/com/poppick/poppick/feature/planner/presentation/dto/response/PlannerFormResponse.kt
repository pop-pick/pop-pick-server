package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerForm
import com.poppick.poppick.feature.planner.domain.PlannerPolicy
import java.time.LocalDate
import java.time.LocalTime

data class PlannerFormResponse(
    /** 온보딩 값. 온보딩 전이면 null / 빈 배열. */
    val defaults: Defaults,
    val options: Options,
    val visitDateRange: DateRange,
    val startTimeRange: TimeRange,
) {
    data class Defaults(
        val areaId: Int?,
        val interestCategoryIds: List<Int>,
        val preferredActivityIds: List<Int>,
    )

    data class Options(
        val areas: List<IdName>,
        val interestCategories: List<IdName>,
        val preferredActivities: List<IdName>,
        val accompanyTypes: List<CodeLabel>,
        val durationTypes: List<CodeLabel>,
    )

    data class IdName(
        val id: Int,
        val name: String,
    )

    data class CodeLabel(
        val code: String,
        val label: String,
        /** 부가 설명. durationTypes 만 채운다. */
        val description: String? = null,
    )

    data class DateRange(
        val min: LocalDate,
        val max: LocalDate,
    )

    data class TimeRange(
        @field:JsonFormat(pattern = "HH:mm")
        val min: LocalTime,
        @field:JsonFormat(pattern = "HH:mm")
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
