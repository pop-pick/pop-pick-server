package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.global.util.KST
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** "내 일정" 목록 항목. stops 전체 대신 개수와 첫 방문지만. */
data class PlannerSummaryResponse(
    @field:Schema(description = "플래너 id", example = "12")
    val plannerId: Long,
    @field:Schema(description = "상태. 목록에는 SCHEDULED · CANCELED 만 나온다", example = "SCHEDULED")
    val status: PlannerStatus,
    @field:Schema(description = "코스 제목", example = "성수 감성 팝업 코스")
    val title: String,
    @field:Schema(description = "지역")
    val area: PlannerResponse.AreaResponse,
    @field:Schema(description = "방문일", example = "2026-10-03")
    val visitDate: LocalDate,
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(description = "시작 시각 HH:mm", example = "14:00")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(description = "종료 시각 HH:mm", example = "17:20")
    val endTime: LocalTime,
    @field:Schema(description = "총 소요(분). 체류 + 이동", example = "200")
    val totalMin: Int,
    @field:Schema(description = "방문지 수", example = "3")
    val stopCount: Int,
    @field:Schema(description = "첫 방문지(카드 썸네일용). 방문지가 없으면 NULL")
    val firstStop: FirstStopResponse?,
    @field:Schema(description = "등록일(확정 시각, KST ISO-8601)", example = "2026-09-28T19:42:10+09:00")
    val confirmedAt: OffsetDateTime?,
    @field:Schema(description = "취소 시각(KST ISO-8601). CANCELED 만 값이 있다", example = "2026-09-29T09:00:00.123+09:00")
    val canceledAt: OffsetDateTime?,
) {
    data class FirstStopResponse(
        @field:Schema(description = "첫 방문지 이름", example = "오래오래 함께가게")
        val title: String,
        @field:Schema(description = "첫 방문지 이미지. 없으면 NULL", example = "https://img.example/1.jpg")
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
                confirmedAt = summary.confirmedAt?.atZoneSameInstant(KST)?.toOffsetDateTime(),
                canceledAt = summary.canceledAt?.atZoneSameInstant(KST)?.toOffsetDateTime(),
            )
    }
}
