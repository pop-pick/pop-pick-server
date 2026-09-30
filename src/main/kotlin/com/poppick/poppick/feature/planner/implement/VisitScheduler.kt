package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.PlannedCourse
import com.poppick.poppick.feature.planner.domain.PlannedStop
import com.poppick.poppick.feature.popup.domain.RouteLeg
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil

/**
 * 시작 시각부터 체류 · 이동을 누적해 방문 시각을 정한다(순수 계산, 시각은 KST).
 * 운영시간 검증은 TODO(#이슈): Popup.openingHours 가 "월~목 10:30~20:00, 금~일 10:30~20:30" 같은 비정형 문자열이라
 * 파싱이 별도 작업이다. 그때까지 응답에 원문을 그대로 내려 앱이 표시한다.
 */
@Component
class VisitScheduler {
    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
    }

    fun schedule(
        draft: CourseDraft,
        legs: List<RouteLeg>,
        visitDate: LocalDate,
        startTime: LocalTime,
    ): PlannedCourse {
        require(draft.stops.isNotEmpty()) { "방문할 팝업이 없습니다." }
        require(legs.size == draft.stops.size - 1) { "구간 수 ${legs.size} 가 stops ${draft.stops.size} - 1 과 다릅니다." }

        val startMinute = startTime.toSecondOfDay() / 60
        var elapsed = 0
        val stops =
            draft.stops.mapIndexed { index, stop ->
                val leg = legs.getOrNull(index)
                val travelMin = leg?.let { ceil(it.timeSec / 60.0).toInt() }
                PlannedStop(
                    popup = stop.popup,
                    visitOrder = index + 1,
                    visitAt = startTime.plusMinutes(elapsed.toLong()),
                    stayMin = stop.stayMin,
                    reason = stop.reason,
                    nextTravelMin = travelMin,
                    nextTravelM = leg?.distanceM,
                    nextPath = leg?.path,
                ).also { elapsed += stop.stayMin + (travelMin ?: 0) }
            }

        // 마지막 팝업은 이동이 없으므로 elapsed = 체류 + 이동 합계 = totalMin.
        check(startMinute + elapsed < MINUTES_PER_DAY) { "코스가 자정을 넘깁니다. start=$startTime totalMin=$elapsed" }

        return PlannedCourse(
            title = draft.title,
            summary = draft.summary,
            visitDate = visitDate,
            startTime = startTime,
            endTime = startTime.plusMinutes(elapsed.toLong()),
            totalMin = elapsed,
            totalTravelM = legs.sumOf { it.distanceM },
            stops = stops,
        )
    }
}
