package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.popup.domain.GeoPoint
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** 저장된 플래너(planner + planner_popup). 시각은 전부 KST. */
data class Planner(
    val id: Long?,
    val memberKey: String,
    val status: PlannerStatus,
    val title: String,
    val summary: String,
    /** favorite_area id. */
    val areaId: Int,
    val accompanyType: AccompanyType,
    val durationType: DurationType,
    val visitDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    /** 사용자 자유 입력. */
    val requestNote: String?,
    val totalMin: Int,
    val totalTravelM: Int,
    val createdAt: OffsetDateTime,
    val confirmedAt: OffsetDateTime?,
    val canceledAt: OffsetDateTime?,
    /** 방문 순서. */
    val stops: List<PlannerStop>,
) {
    fun isOwnedBy(memberKey: String) = this.memberKey == memberKey

    companion object {
        // 컬럼 길이(planner.title 50 · summary 120 · request_note 200, planner_popup.title 200 · address 200 · reason 100 ·
        // image_url 500 · opening_hours 200). 앞 단계에서 이미 짧게 잘리지만 저장 직전에 한 번 더 맞춘다.
        private const val MAX_TITLE = 50
        private const val MAX_SUMMARY = 120
        private const val MAX_NOTE = 200
        private const val MAX_STOP_TITLE = 200
        private const val MAX_ADDRESS = 200
        private const val MAX_REASON = 100
        private const val MAX_IMAGE_URL = 500
        private const val MAX_OPENING_HOURS = 200

        /** 생성 결과를 DRAFT 로. 방문지 표시 필드는 지금 팝업 값의 스냅샷(주소는 도로명 우선, 이미지는 첫 장). */
        fun draft(
            memberKey: String,
            command: PlannerGenerateCommand,
            course: PlannedCourse,
            now: OffsetDateTime,
        ) = Planner(
            id = null,
            memberKey = memberKey,
            status = PlannerStatus.DRAFT,
            title = course.title.take(MAX_TITLE),
            summary = course.summary.take(MAX_SUMMARY),
            areaId = command.areaId,
            accompanyType = command.accompanyType,
            durationType = command.durationType,
            visitDate = course.visitDate,
            startTime = course.startTime,
            endTime = course.endTime,
            requestNote =
                command.note
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.take(MAX_NOTE),
            totalMin = course.totalMin,
            totalTravelM = course.totalTravelM,
            createdAt = now,
            confirmedAt = null,
            canceledAt = null,
            stops =
                course.stops.map { stop ->
                    val popup = stop.popup
                    PlannerStop(
                        id = null,
                        popupId = popup.id,
                        visitOrder = stop.visitOrder,
                        visitAt = stop.visitAt,
                        stayMin = stop.stayMin,
                        reason = stop.reason.take(MAX_REASON),
                        title = popup.title.take(MAX_STOP_TITLE),
                        address = (popup.addressRoad?.takeIf { it.isNotBlank() } ?: popup.addressJibun)?.take(MAX_ADDRESS),
                        latitude = requireNotNull(popup.latitude) { "좌표 없는 팝업 popupId=${popup.id}" },
                        longitude = requireNotNull(popup.longitude) { "좌표 없는 팝업 popupId=${popup.id}" },
                        // URL 은 자르면 깨지므로 길면 버린다.
                        imageUrl = popup.imageUrls?.firstOrNull { it.isNotBlank() && it.length <= MAX_IMAGE_URL },
                        openingHours = popup.openingHours?.take(MAX_OPENING_HOURS),
                        nextTravelMin = stop.nextTravelMin,
                        nextTravelM = stop.nextTravelM,
                        nextPath = stop.nextPath,
                    )
                },
        )
    }
}

/**
 * 플래너의 방문지 1곳. title · address · 좌표 · imageUrl · openingHours 는 저장 시점 스냅샷이다
 * (PopupPurger 가 종료 팝업을 물리 삭제하므로 일정 화면은 이 값으로 그린다).
 */
data class PlannerStop(
    val id: Long?,
    /** 팝업이 삭제됐으면 NULL. */
    val popupId: Long?,
    /** 1부터. */
    val visitOrder: Int,
    val visitAt: LocalTime,
    val stayMin: Int,
    val reason: String,
    val title: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val openingHours: String?,
    /** 다음 방문지까지 도보(분). 마지막은 NULL. */
    val nextTravelMin: Int?,
    val nextTravelM: Int?,
    /** 다음 방문지까지 경로. 저장만 하고 API 로는 내리지 않는다. */
    val nextPath: List<GeoPoint>?,
)
