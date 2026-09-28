package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerEntity.plannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.QPlannerPopupEntity.plannerPopupEntity
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport

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
}
