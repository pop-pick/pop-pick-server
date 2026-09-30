package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.PlannedCourse
import org.springframework.stereotype.Component

/** 코스 초안에 도보 경로를 붙이고 방문 시각을 배치한다. PlannerGenerator 가 CandidatePopupFinder → CourseComposer 다음에 부른다. */
@Component
class CoursePlanner(
    private val courseRouter: CourseRouter,
    private val visitScheduler: VisitScheduler,
) {
    fun plan(
        condition: CourseCondition,
        draft: CourseDraft,
    ): PlannedCourse = visitScheduler.schedule(draft, courseRouter.route(draft), condition.visitDate, condition.startTime)
}
