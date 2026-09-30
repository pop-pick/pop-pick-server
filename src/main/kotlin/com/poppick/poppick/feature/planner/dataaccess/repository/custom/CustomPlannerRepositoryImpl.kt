package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerPopupEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerEntity.plannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerPopupEntity.plannerPopupEntity
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerTabCounts
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import com.poppick.poppick.global.util.KST
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import java.time.LocalDate
import java.time.OffsetDateTime

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

    override fun countByTab(
        memberKey: String,
        today: LocalDate,
    ): PlannerTabCounts {
        val scheduled = plannerEntity.status.eq(PlannerStatus.SCHEDULED)
        val upcoming = countWhen(scheduled.and(plannerEntity.visitDate.goe(today)))
        val past = countWhen(scheduled.and(plannerEntity.visitDate.lt(today)))
        val canceled = countWhen(plannerEntity.status.eq(PlannerStatus.CANCELED))
        // status IN 조건으로 idx_planner_member_status(member_key, status, visit_date) 범위만 읽는다.
        val row =
            select(Projections.tuple(upcoming, past, canceled))
                .from(plannerEntity)
                .where(
                    plannerEntity.memberKey.eq(memberKey),
                    plannerEntity.status.`in`(PlannerStatus.SCHEDULED, PlannerStatus.CANCELED),
                ).fetchOne()
        // 행이 없으면 SUM 은 NULL.
        return PlannerTabCounts(
            upcoming = row?.get(upcoming) ?: 0,
            past = row?.get(past) ?: 0,
            canceled = row?.get(canceled) ?: 0,
        )
    }

    // JPQL SUM(정수) 는 Long.
    private fun countWhen(condition: BooleanExpression): NumberExpression<Long> =
        Expressions.numberTemplate(Long::class.javaObjectType, "sum(case when {0} then 1 else 0 end)", condition)

    override fun deleteDrafts(memberKey: String): Long = deleteDraftsWhere(plannerEntity.memberKey.eq(memberKey))

    override fun deleteDraftsCreatedBefore(olderThan: OffsetDateTime): Long = deleteDraftsWhere(plannerEntity.createdAt.lt(olderThan))

    // JPQL bulk delete 는 JPA cascade · 영속성 컨텍스트를 거치지 않는다. 방문지는 DB FK CASCADE 로 지우고,
    // 이미 올라와 있는 엔티티가 지워진 행을 가리키지 않게 끝나면 컨텍스트를 비운다(@Modifying(clearAutomatically) 와 같은 효과).
    private fun deleteDraftsWhere(condition: BooleanExpression): Long {
        flush()
        return delete(plannerEntity)
            .where(plannerEntity.status.eq(PlannerStatus.DRAFT), condition)
            .execute()
            .also { clear() }
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
