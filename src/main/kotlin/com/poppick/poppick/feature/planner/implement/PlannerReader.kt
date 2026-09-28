package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.feature.planner.domain.PlannerSummaryPage
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class PlannerReader(
    private val plannerRepository: PlannerRepository,
) {
    /** 없으면 PLANNER_NOT_FOUND. 소유자 확인은 호출 측이 한다. */
    @Transactional(readOnly = true)
    fun get(id: Long): Planner = plannerRepository.findByIdWithStops(id)?.toDomain() ?: throw AppException(ErrorType.PLANNER_NOT_FOUND)

    /** 회원의 확정(SCHEDULED) 일정에 들어간 팝업 id. 다음 추천에서 뺀다. */
    fun findScheduledPopupIds(memberKey: String): Set<Long> = plannerRepository.findScheduledPopupIds(memberKey)

    /**
     * "내 일정" 목록 한 페이지. limit + 1 건을 읽어 다음 페이지 유무를 판단한다.
     * 방문지는 개수 · 첫 방문지만 IN 두 번으로 붙인다(N+1 없음).
     */
    @Transactional(readOnly = true)
    fun list(
        memberKey: String,
        tab: PlannerListTab,
        cursor: PlannerListCursor?,
        limit: Int,
        today: LocalDate,
    ): PlannerSummaryPage {
        val rows = plannerRepository.findList(memberKey, tab, cursor, today, limit + 1)
        val page = rows.take(limit)
        val ids = page.mapNotNull { it.id }
        val counts = plannerRepository.countStops(ids)
        val firstStops = plannerRepository.findFirstStops(ids)

        val content =
            page.map { planner ->
                val id = planner.id!!
                PlannerSummary(
                    id = id,
                    status = planner.status,
                    title = planner.title,
                    areaId = planner.areaId,
                    visitDate = planner.visitDate,
                    startTime = planner.startTime,
                    endTime = planner.endTime,
                    totalMin = planner.totalMin,
                    stopCount = counts[id] ?: 0,
                    firstStop = firstStops[id]?.let { PlannerSummary.FirstStop(it.title, it.imageUrl) },
                    canceledAt = planner.canceledAt,
                )
            }
        val hasNext = rows.size > limit
        return PlannerSummaryPage(
            content = content,
            hasNext = hasNext,
            nextCursor = if (hasNext) PlannerListCursor.after(tab, content.last()).encode() else null,
        )
    }
}
