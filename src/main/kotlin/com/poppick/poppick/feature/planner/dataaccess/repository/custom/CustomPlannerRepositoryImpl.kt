package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerPopupEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerEntity.plannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerPopupEntity.plannerPopupEntity
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import com.poppick.poppick.global.util.KST
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import java.time.LocalDate

class CustomPlannerRepositoryImpl :
    QuerydslRepositorySupport(PlannerEntity::class),
    CustomPlannerRepository {
    override fun findByIdWithStops(id: Long): PlannerEntity? =
        selectFrom(plannerEntity)
            .leftJoin(plannerEntity.stops, plannerPopupEntity)
            .fetchJoin()
            .where(plannerEntity.id.eq(id))
            .fetchOne()

    override fun findScheduledPopupIds(memberKey: String): Set<Long> =
        select(plannerPopupEntity.popupId)
            .from(plannerPopupEntity)
            .join(plannerPopupEntity.planner(), plannerEntity)
            .where(
                plannerEntity.memberKey.eq(memberKey),
                plannerEntity.status.eq(PlannerStatus.SCHEDULED),
                plannerPopupEntity.popupId.isNotNull,
            ).distinct()
            .fetch()
            .filterNotNull()
            .toSet()

    override fun findList(
        memberKey: String,
        tab: PlannerListTab,
        cursor: PlannerListCursor?,
        today: LocalDate,
        limit: Int,
    ): List<PlannerEntity> {
        val (condition, order) =
            when (tab) {
                PlannerListTab.UPCOMING ->
                    plannerEntity.status.eq(PlannerStatus.SCHEDULED).and(plannerEntity.visitDate.goe(today)) to
                        listOf(plannerEntity.visitDate.asc(), plannerEntity.startTime.asc(), plannerEntity.id.asc())
                PlannerListTab.PAST ->
                    plannerEntity.status.eq(PlannerStatus.SCHEDULED).and(plannerEntity.visitDate.lt(today)) to
                        listOf(plannerEntity.visitDate.desc(), plannerEntity.startTime.desc(), plannerEntity.id.desc())
                PlannerListTab.CANCELED ->
                    plannerEntity.status.eq(PlannerStatus.CANCELED) to
                        listOf(plannerEntity.canceledAt.desc(), plannerEntity.id.desc())
            }

        return selectFrom(plannerEntity)
            .where(plannerEntity.memberKey.eq(memberKey), condition, cursor?.let { after(tab, it) })
            .orderBy(*order.toTypedArray<OrderSpecifier<*>>())
            .limit(limit.toLong())
            .fetch()
    }

    // keyset: 정렬 키 튜플이 커서보다 뒤인 행.
    private fun after(
        tab: PlannerListTab,
        cursor: PlannerListCursor,
    ): BooleanExpression =
        when (cursor) {
            is PlannerListCursor.Visit -> {
                val ascending = tab == PlannerListTab.UPCOMING
                val date = if (ascending) plannerEntity.visitDate.gt(cursor.visitDate) else plannerEntity.visitDate.lt(cursor.visitDate)
                val time = if (ascending) plannerEntity.startTime.gt(cursor.startTime) else plannerEntity.startTime.lt(cursor.startTime)
                val id = if (ascending) plannerEntity.id.gt(cursor.plannerId) else plannerEntity.id.lt(cursor.plannerId)
                date
                    .or(plannerEntity.visitDate.eq(cursor.visitDate).and(time))
                    .or(
                        plannerEntity.visitDate
                            .eq(cursor.visitDate)
                            .and(plannerEntity.startTime.eq(cursor.startTime))
                            .and(id),
                    )
            }
            is PlannerListCursor.Canceled -> {
                val canceledAt = cursor.canceledAt.atZone(KST).toOffsetDateTime()
                plannerEntity.canceledAt
                    .lt(canceledAt)
                    .or(plannerEntity.canceledAt.eq(canceledAt).and(plannerEntity.id.lt(cursor.plannerId)))
            }
        }

    override fun countStops(plannerIds: Collection<Long>): Map<Long, Int> {
        if (plannerIds.isEmpty()) return emptyMap()
        val plannerId = plannerPopupEntity.planner().id
        val count = plannerPopupEntity.count()
        return select(Projections.tuple(plannerId, count))
            .from(plannerPopupEntity)
            .where(plannerId.`in`(plannerIds))
            .groupBy(plannerId)
            .fetch()
            .associate { it.get(plannerId)!! to it.get(count)!!.toInt() }
    }

    override fun findFirstStops(plannerIds: Collection<Long>): Map<Long, PlannerPopupEntity> {
        if (plannerIds.isEmpty()) return emptyMap()
        return selectFrom(plannerPopupEntity)
            .where(plannerPopupEntity.planner().id.`in`(plannerIds), plannerPopupEntity.visitOrder.eq(1))
            .fetch()
            .associateBy { it.planner.id!! }
    }
}
