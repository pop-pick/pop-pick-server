package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.global.util.KST
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** "내 일정" 목록 항목. stops 전체 대신 개수와 첫 방문지만. */
data class PlannerSummaryResponse(
    val plannerId: Long,
    val status: PlannerStatus,
    val title: String,
    val area: PlannerResponse.AreaResponse,
    val visitDate: LocalDate,
    @field:JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    val endTime: LocalTime,
    val totalMin: Int,
    val stopCount: Int,
    val firstStop: FirstStopResponse?,
    /** CANCELED 탭에서만 값이 있다. KST. */
    val canceledAt: OffsetDateTime?,
) {
    data class FirstStopResponse(
        val title: String,
        val imageUrl: String?,
    )

    companion object {
        fun from(summary: PlannerSummary) =
            PlannerSummaryResponse(
                plannerId = summary.id,
                status = summary.status,
                title = summary.title,
                area = PlannerResponse.AreaResponse(summary.areaId, summary.areaName),
                visitDate = summary.visitDate,
                startTime = summary.startTime,
                endTime = summary.endTime,
                totalMin = summary.totalMin,
                stopCount = summary.stopCount,
                firstStop = summary.firstStop?.let { FirstStopResponse(it.title, it.imageUrl) },
                canceledAt = summary.canceledAt?.atZoneSameInstant(KST)?.toOffsetDateTime(),
            )
    }
}
