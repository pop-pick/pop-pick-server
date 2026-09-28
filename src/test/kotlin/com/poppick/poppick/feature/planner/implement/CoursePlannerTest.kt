package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannedCourse
import com.poppick.poppick.feature.popup.domain.RouteLeg
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import java.time.LocalDate
import java.time.LocalTime

class CoursePlannerTest :
    FunSpec({
        test("route 결과를 그대로 schedule 에 넘긴다") {
            val condition = CourseCondition(LocalDate.of(2026, 10, 3), LocalTime.of(14, 0), AccompanyType.ALONE, DurationType.SHORT, null)
            val draft = CourseDraft("코스", "소개", emptyList())
            val legs = listOf(RouteLeg(100, 60, emptyList()))
            val planned = mockk<PlannedCourse>()
            val router = mockk<CourseRouter> { every { route(draft) } returns legs }
            val scheduler =
                mockk<VisitScheduler> {
                    every { schedule(draft, legs, condition.visitDate, condition.startTime) } returns
                        planned
                }

            CoursePlanner(router, scheduler).plan(condition, draft) shouldBe planned
            verifyOrder {
                router.route(draft)
                scheduler.schedule(draft, legs, condition.visitDate, condition.startTime)
            }
        }
    })
