package com.poppick.poppick.feature.planner

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerStop
import com.poppick.poppick.feature.popup.domain.GeoPoint
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

object PlannerFixtures {
    val createdAt: OffsetDateTime = OffsetDateTime.of(2026, 9, 28, 19, 40, 0, 0, ZoneOffset.ofHours(9))

    fun stop(
        order: Int,
        last: Boolean = false,
    ) = PlannerStop(
        id = order.toLong(),
        popupId = 1700L + order,
        visitOrder = order,
        visitAt = LocalTime.of(14, 0).plusMinutes((order - 1) * 70L),
        stayMin = 60,
        reason = "추천 이유 $order",
        title = "팝업 $order",
        address = "서울 성동구 성수동2가 30$order-13",
        latitude = 37.54,
        longitude = 127.05,
        imageUrl = null,
        openingHours = "매일 11:00~20:00",
        nextTravelMin = if (last) null else 10,
        nextTravelM = if (last) null else 668,
        nextPath = if (last) null else listOf(GeoPoint(37.54, 127.05), GeoPoint(37.55, 127.06)),
    )

    fun planner(
        id: Long? = 12,
        memberKey: String = "member-1",
        status: PlannerStatus = PlannerStatus.DRAFT,
        visitDate: LocalDate = LocalDate.of(2026, 10, 3),
        confirmedAt: OffsetDateTime? = null,
    ) = Planner(
        id = id,
        memberKey = memberKey,
        status = status,
        title = "성수 코스",
        summary = "성수 팝업을 둘러보는 코스",
        areaId = 1,
        accompanyType = AccompanyType.WITH_FRIEND,
        durationType = DurationType.HALF_DAY,
        visitDate = visitDate,
        startTime = LocalTime.of(14, 0),
        endTime = LocalTime.of(17, 20),
        requestNote = "향수 만들기",
        totalMin = 200,
        totalTravelM = 1336,
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        canceledAt = null,
        stops = listOf(stop(1), stop(2), stop(3, last = true)),
    )
}
