package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerStop
import com.poppick.poppick.global.util.KST
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** 플래너 상세. 경로 좌표(nextPath)는 앱이 쓰지 않아 내리지 않는다(저장만). */
data class PlannerResponse(
    val plannerId: Long,
    val status: PlannerStatus,
    val title: String,
    val summary: String,
    val area: AreaResponse,
    val accompanyType: AccompanyType,
    val durationType: DurationType,
    val visitDate: LocalDate,
    @field:JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    val endTime: LocalTime,
    val totalMin: Int,
    val totalTravelM: Int,
    val requestNote: String?,
    val stops: List<StopResponse>,
    /** KST 오프셋(+09:00)으로 내린다. */
    val createdAt: OffsetDateTime,
) {
    data class AreaResponse(
        val id: Int,
        val name: String?,
    )

    data class StopResponse(
        val visitOrder: Int,
        /** 팝업이 삭제됐으면 NULL. */
        val popupId: Long?,
        val title: String,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val imageUrl: String?,
        val openingHours: String?,
        @field:JsonFormat(pattern = "HH:mm")
        val visitAt: LocalTime,
        val stayMin: Int,
        val reason: String,
        /** 마지막은 NULL. */
        val nextTravelMin: Int?,
        val nextTravelM: Int?,
    ) {
        companion object {
            fun from(stop: PlannerStop) =
                StopResponse(
                    visitOrder = stop.visitOrder,
                    popupId = stop.popupId,
                    title = stop.title,
                    address = stop.address,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    imageUrl = stop.imageUrl,
                    openingHours = stop.openingHours,
                    visitAt = stop.visitAt,
                    stayMin = stop.stayMin,
                    reason = stop.reason,
                    nextTravelMin = stop.nextTravelMin,
                    nextTravelM = stop.nextTravelM,
                )
        }
    }

    companion object {
        fun from(detail: PlannerDetail): PlannerResponse {
            val planner = detail.planner
            return PlannerResponse(
                plannerId = requireNotNull(planner.id),
                status = planner.status,
                title = planner.title,
                summary = planner.summary,
                area = AreaResponse(planner.areaId, detail.areaName),
                accompanyType = planner.accompanyType,
                durationType = planner.durationType,
                visitDate = planner.visitDate,
                startTime = planner.startTime,
                endTime = planner.endTime,
                totalMin = planner.totalMin,
                totalTravelM = planner.totalTravelM,
                requestNote = planner.requestNote,
                stops = planner.stops.map { StopResponse.from(it) },
                createdAt = planner.createdAt.atZoneSameInstant(KST).toOffsetDateTime(),
            )
        }
    }
}
