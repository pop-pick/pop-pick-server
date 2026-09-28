package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.global.util.KST
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 비로그인 공유 조회. PlannerResponse 에서 plannerId · requestNote 를 뺐다(내부 id 와 작성자가 쓴 요청 문구를 노출하지 않는다).
 * 회원 식별 정보는 넣지 않는다.
 */
data class SharedPlannerResponse(
    val status: PlannerStatus,
    val title: String,
    val summary: String,
    val area: PlannerResponse.AreaResponse,
    val accompanyType: AccompanyType,
    val durationType: DurationType,
    val visitDate: LocalDate,
    @field:JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    @field:JsonFormat(pattern = "HH:mm")
    val endTime: LocalTime,
    val totalMin: Int,
    val totalTravelM: Int,
    val stops: List<PlannerResponse.StopResponse>,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(detail: PlannerDetail): SharedPlannerResponse {
            val planner = detail.planner
            return SharedPlannerResponse(
                status = planner.status,
                title = planner.title,
                summary = planner.summary,
                area = PlannerResponse.AreaResponse(planner.areaId, detail.areaName),
                accompanyType = planner.accompanyType,
                durationType = planner.durationType,
                visitDate = planner.visitDate,
                startTime = planner.startTime,
                endTime = planner.endTime,
                totalMin = planner.totalMin,
                totalTravelM = planner.totalTravelM,
                stops = planner.stops.map { PlannerResponse.StopResponse.from(it) },
                createdAt = planner.createdAt.atZoneSameInstant(KST).toOffsetDateTime(),
            )
        }
    }
}
