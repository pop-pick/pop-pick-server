package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlannerReader(
    private val plannerRepository: PlannerRepository,
) {
    /** 없으면 PLANNER_NOT_FOUND. 소유자 확인은 호출 측이 한다. */
    @Transactional(readOnly = true)
    fun get(id: Long): Planner = plannerRepository.findByIdWithStops(id)?.toDomain() ?: throw AppException(ErrorType.PLANNER_NOT_FOUND)

    /** 회원의 확정(SCHEDULED) 일정에 들어간 팝업 id. 다음 추천에서 뺀다. */
    fun findScheduledPopupIds(memberKey: String): Set<Long> = plannerRepository.findScheduledPopupIds(memberKey)
}
