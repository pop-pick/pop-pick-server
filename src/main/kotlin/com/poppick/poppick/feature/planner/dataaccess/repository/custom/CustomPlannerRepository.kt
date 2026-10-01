package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerPopupEntity
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerTabCounts
import java.time.LocalDate
import java.time.OffsetDateTime

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

    /** 탭별 건수를 쿼리 한 번으로(CASE 합계 3개). 조건은 findList 와 같고 DRAFT 는 세지 않는다. */
    fun countByTab(
        memberKey: String,
        today: LocalDate,
    ): PlannerTabCounts

    /**
     * 회원의 DRAFT 를 전부 물리 삭제(bulk). planner_popup 은 FK CASCADE 로 지워진다. 삭제 건수를 돌려준다.
     * 영속성 컨텍스트를 비우므로 트랜잭션 안에서 먼저 불러야 한다.
     */
    fun deleteDrafts(memberKey: String): Long

    /** created_at < olderThan 인 DRAFT 를 전부 물리 삭제(bulk). 삭제 건수를 돌려준다. */
    fun deleteDraftsCreatedBefore(olderThan: OffsetDateTime): Long

    /** 플래너별 방문지 수. 목록 N+1 을 피하려고 IN 한 번으로. */
    fun countStops(plannerIds: Collection<Long>): Map<Long, Int>

    /** 플래너별 첫 방문지(visit_order = 1). IN 한 번으로. */
    fun findFirstStops(plannerIds: Collection<Long>): Map<Long, PlannerPopupEntity>
}
