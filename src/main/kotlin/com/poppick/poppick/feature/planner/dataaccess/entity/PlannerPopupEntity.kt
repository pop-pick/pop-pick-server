package com.poppick.poppick.feature.planner.dataaccess.entity

import com.poppick.poppick.feature.planner.domain.PlannerStop
import com.poppick.poppick.feature.popup.domain.GeoPoint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalTime

/** 플래너 방문지 1곳. 표시용 필드는 저장 시점 팝업 스냅샷. */
@Entity
@Table(name = "planner_popup")
class PlannerPopupEntity(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planner_id", nullable = false)
    var planner: PlannerEntity,
    /** 팝업이 삭제되면 NULL(ON DELETE SET NULL). */
    var popupId: Long? = null,
    @Column(nullable = false)
    var visitOrder: Int,
    @Column(nullable = false)
    var visitAt: LocalTime,
    @Column(nullable = false)
    var stayMin: Int,
    @Column(nullable = false)
    var reason: String,
    @Column(nullable = false)
    var title: String,
    var address: String? = null,
    @Column(nullable = false)
    var latitude: Double,
    @Column(nullable = false)
    var longitude: Double,
    var imageUrl: String? = null,
    var openingHours: String? = null,
    var nextTravelMin: Int? = null,
    @Column(name = "next_travel_m")
    var nextTravelM: Int? = null,
    /**
     * 다음 방문지까지 경로. jsonb 에 [{"lat": .., "lng": ..}, ...] 로 저장한다.
     * popup.raw_payload 처럼 Map 기반으로 둬서 JSON 매퍼가 도메인 클래스 생성 방식을 몰라도 읽히게 한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    var nextPath: List<Map<String, Number>>? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planner_popup_id")
    var id: Long? = null,
) {
    companion object {
        private const val LAT = "lat"
        private const val LNG = "lng"

        fun from(
            stop: PlannerStop,
            planner: PlannerEntity,
        ) = PlannerPopupEntity(
            planner = planner,
            popupId = stop.popupId,
            visitOrder = stop.visitOrder,
            visitAt = stop.visitAt,
            stayMin = stop.stayMin,
            reason = stop.reason,
            title = stop.title,
            address = stop.address,
            latitude = stop.latitude,
            longitude = stop.longitude,
            imageUrl = stop.imageUrl,
            openingHours = stop.openingHours,
            nextTravelMin = stop.nextTravelMin,
            nextTravelM = stop.nextTravelM,
            nextPath = stop.nextPath?.map { mapOf(LAT to it.lat, LNG to it.lng) },
            id = stop.id,
        )
    }

    fun toDomain() =
        PlannerStop(
            id = id,
            popupId = popupId,
            visitOrder = visitOrder,
            visitAt = visitAt,
            stayMin = stayMin,
            reason = reason,
            title = title,
            address = address,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            openingHours = openingHours,
            nextTravelMin = nextTravelMin,
            nextTravelM = nextTravelM,
            // jsonb 에서 정수 좌표는 Integer 로 읽히므로 Number 로 받아 변환한다.
            nextPath = nextPath?.map { GeoPoint(lat = it.getValue(LAT).toDouble(), lng = it.getValue(LNG).toDouble()) },
        )
}
