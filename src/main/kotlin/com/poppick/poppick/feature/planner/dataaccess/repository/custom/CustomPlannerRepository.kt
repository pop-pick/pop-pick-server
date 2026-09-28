package com.poppick.poppick.feature.planner.dataaccess.repository.custom

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity

interface CustomPlannerRepository {
    /** planner + stops(visit_order 순) 를 한 번에. */
    fun findByIdWithStops(id: Long): PlannerEntity?

    /**
     * 회원의 SCHEDULED 플래너에 들어간 popup_id(지난 일정 포함). DRAFT · CANCELED 와 popup_id NULL(삭제된 팝업)은 뺀다.
     * 다음 추천에서 제외할 팝업 목록으로 쓴다.
     */
    fun findScheduledPopupIds(memberKey: String): Set<Long>
}
