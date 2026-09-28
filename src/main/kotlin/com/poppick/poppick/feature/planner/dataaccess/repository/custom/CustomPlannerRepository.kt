package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerPopupEntity
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import java.time.LocalDate

interface CustomPlannerRepository {
    /** planner + stops(visit_order 순) 를 한 번에. */
    fun findByIdWithStops(id: Long): PlannerEntity?

    /**
     * 회원의 SCHEDULED 플래너에 들어간 popup_id(지난 일정 포함). DRAFT · CANCELED 와 popup_id NULL(삭제된 팝업)은 뺀다.
     * 다음 추천에서 제외할 팝업 목록으로 쓴다.
     */
    fun findScheduledPopupIds(memberKey: String): Set<Long>

    /**
     * "내 일정" 목록(stops 없이). 커서 다음부터 limit 건, tab 정렬 순.
     * - UPCOMING: SCHEDULED, visit_date >= today, (visit_date, start_time, id) 오름차순
     * - PAST: SCHEDULED, visit_date < today, (visit_date, start_time, id) 내림차순
     * - CANCELED: CANCELED, (canceled_at, id) 내림차순
     */
    fun findList(
        memberKey: String,
        tab: PlannerListTab,
        cursor: PlannerListCursor?,
        today: LocalDate,
        limit: Int,
    ): List<PlannerEntity>

    /** 플래너별 방문지 수. 목록 N+1 을 피하려고 IN 한 번으로. */
    fun countStops(plannerIds: Collection<Long>): Map<Long, Int>

    /** 플래너별 첫 방문지(visit_order = 1). IN 한 번으로. */
    fun findFirstStops(plannerIds: Collection<Long>): Map<Long, PlannerPopupEntity>

    /** planner + stops. 공유 조회용. */
    fun findByShareTokenWithStops(shareToken: String): PlannerEntity?
}
