package com.poppick.poppick.feature.planner.dataaccess.entity

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.global.util.KST
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/** 테이블의 created_at · updated_at 이 BaseEntity(created_at · last_modified_at, LocalDateTime) 와 달라 직접 둔다. */
@Entity
@Table(name = "planner")
class PlannerEntity(
    @Column(nullable = false)
    var memberKey: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PlannerStatus,
    @Column(nullable = false)
    var title: String,
    @Column(nullable = false)
    var summary: String,
    @Column(nullable = false)
    var areaId: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var accompanyType: AccompanyType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var durationType: DurationType,
    @Column(nullable = false)
    var visitDate: LocalDate,
    @Column(nullable = false)
    var startTime: LocalTime,
    @Column(nullable = false)
    var endTime: LocalTime,
    var requestNote: String? = null,
    @Column(nullable = false)
    var totalMin: Int,
    @Column(name = "total_travel_m", nullable = false)
    var totalTravelM: Int,
    var shareToken: String? = null,
    @Column(nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(KST),
    @Column(nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(KST),
    var confirmedAt: OffsetDateTime? = null,
    var canceledAt: OffsetDateTime? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "planner_id")
    var id: Long? = null,
) {
    /** 방문 순서대로. 저장 · 삭제는 planner 와 함께(cascade). */
    @OneToMany(mappedBy = "planner", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("visitOrder ASC")
    var stops: MutableList<PlannerPopupEntity> = mutableListOf()

    companion object {
        fun from(planner: Planner) =
            PlannerEntity(
                memberKey = planner.memberKey,
                status = planner.status,
                title = planner.title,
                summary = planner.summary,
                areaId = planner.areaId,
                accompanyType = planner.accompanyType,
                durationType = planner.durationType,
                visitDate = planner.visitDate,
                startTime = planner.startTime,
                endTime = planner.endTime,
                requestNote = planner.requestNote,
                totalMin = planner.totalMin,
                totalTravelM = planner.totalTravelM,
                shareToken = planner.shareToken,
                createdAt = planner.createdAt,
                updatedAt = planner.createdAt,
                confirmedAt = planner.confirmedAt,
                canceledAt = planner.canceledAt,
                id = planner.id,
            ).also { entity -> entity.stops += planner.stops.map { PlannerPopupEntity.from(it, entity) } }
    }

    @PreUpdate
    fun touch() {
        updatedAt = OffsetDateTime.now(KST)
    }

    fun confirm(now: OffsetDateTime) {
        status = PlannerStatus.SCHEDULED
        confirmedAt = now
    }

    fun cancel(now: OffsetDateTime) {
        status = PlannerStatus.CANCELED
        canceledAt = now
    }

    fun toDomain() =
        Planner(
            id = id,
            memberKey = memberKey,
            status = status,
            title = title,
            summary = summary,
            areaId = areaId,
            accompanyType = accompanyType,
            durationType = durationType,
            visitDate = visitDate,
            startTime = startTime,
            endTime = endTime,
            requestNote = requestNote,
            totalMin = totalMin,
            totalTravelM = totalTravelM,
            shareToken = shareToken,
            createdAt = createdAt,
            confirmedAt = confirmedAt,
            canceledAt = canceledAt,
            stops = stops.sortedBy { it.visitOrder }.map { it.toDomain() },
        )
}
