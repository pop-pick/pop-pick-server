package com.poppick.poppick.feature.planner.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannerDetail
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerStop
import com.poppick.poppick.global.util.KST
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** 플래너 상세. 경로 좌표(nextPath)는 앱이 쓰지 않아 내리지 않는다(저장만). */
data class PlannerResponse(
    @field:Schema(description = "플래너 id", example = "12")
    val plannerId: Long,
    @field:Schema(description = "상태", example = "DRAFT")
    val status: PlannerStatus,
    @field:Schema(description = "코스 제목", example = "성수 감성 팝업 코스")
    val title: String,
    @field:Schema(description = "코스 한 줄 소개", example = "성수 골목의 캐릭터 팝업을 걸어서 둘러보는 코스")
    val summary: String,
    @field:Schema(description = "지역")
    val area: AreaResponse,
    @field:Schema(description = "동행 유형", example = "WITH_FRIEND")
    val accompanyType: AccompanyType,
    @field:Schema(description = "소요시간 유형", example = "HALF_DAY")
    val durationType: DurationType,
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
    @field:Schema(description = "총 도보 거리(m)", example = "1336")
    val totalTravelM: Int,
    @field:Schema(description = "생성 시 추가 요청. 없으면 NULL", example = "향수 만들기 체험이 있으면 좋겠어요")
    val requestNote: String?,
    @field:Schema(description = "방문지(방문 순서)")
    val stops: List<StopResponse>,
    @field:Schema(description = "생성 시각(KST ISO-8601)", example = "2026-09-28T19:40:00+09:00")
    val createdAt: OffsetDateTime,
    @field:Schema(description = "등록일(확정 시각, KST ISO-8601). DRAFT 는 NULL", example = "2026-09-28T19:42:10+09:00")
    val confirmedAt: OffsetDateTime?,
) {
    data class AreaResponse(
        @field:Schema(description = "지역 id", example = "1")
        val id: Int,
        @field:Schema(description = "지역 이름", example = "성수")
        val name: String?,
    )

    data class StopResponse(
        @field:Schema(description = "방문 순서(1부터)", example = "1")
        val visitOrder: Int,
        @field:Schema(description = "팝업 id. 팝업이 삭제됐으면 NULL(나머지 필드는 저장 시점 스냅샷)", example = "1701")
        val popupId: Long?,
        @field:Schema(description = "팝업 이름", example = "오래오래 함께가게")
        val title: String,
        @field:Schema(description = "주소(도로명 우선)", example = "서울 성동구 연무장길 1")
        val address: String?,
        @field:Schema(description = "위도", example = "37.5445")
        val latitude: Double,
        @field:Schema(description = "경도", example = "127.0557")
        val longitude: Double,
        @field:Schema(description = "대표 이미지. 없으면 NULL", example = "https://img.example/1.jpg")
        val imageUrl: String?,
        @field:Schema(description = "운영 시간(원문)", example = "매일 11:00~20:00")
        val openingHours: String?,
        @field:JsonFormat(pattern = "HH:mm")
        @field:Schema(description = "도착 시각 HH:mm", example = "14:00")
        val visitAt: LocalTime,
        @field:Schema(description = "체류(분)", example = "60")
        val stayMin: Int,
        @field:Schema(description = "추천 이유", example = "캐릭터 굿즈를 좋아하는 취향에 맞아요")
        val reason: String,
        @field:Schema(description = "다음 방문지까지 도보(분). 마지막은 NULL", example = "10")
        val nextTravelMin: Int?,
        @field:Schema(description = "다음 방문지까지 도보(m). 마지막은 NULL", example = "668")
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
                confirmedAt = planner.confirmedAt?.atZoneSameInstant(KST)?.toOffsetDateTime(),
            )
        }
    }
}
