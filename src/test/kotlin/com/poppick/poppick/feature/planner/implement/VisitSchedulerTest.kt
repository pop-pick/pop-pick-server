package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.CourseStop
import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.RouteLeg
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime

class VisitSchedulerTest :
    FunSpec({
        val scheduler = VisitScheduler()
        val visitDate = LocalDate.of(2026, 10, 3)
        val path = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.01))

        fun draft(vararg stayMins: Int) =
            CourseDraft(
                title = "성수 코스",
                summary = "소개",
                stops =
                    stayMins.mapIndexed { index, stayMin ->
                        CourseStop(Popup(id = index + 1L, source = SourceType.KAKAO_MAP, title = "팝업$index"), stayMin, "이유$index")
                    },
            )

        test("14:00 시작, 체류 70 · 70 · 70, 이동 8 · 12분 → 14:00 · 15:18 · 16:40, 종료 17:50, 230분") {
            // 8분(480초) · 11분 1초(661초) → 올림 12분
            val legs = listOf(RouteLeg(600, 480, path), RouteLeg(900, 661, path))

            val course = scheduler.schedule(draft(70, 70, 70), legs, visitDate, LocalTime.of(14, 0))

            course.stops.map { it.visitAt } shouldBe listOf(LocalTime.of(14, 0), LocalTime.of(15, 18), LocalTime.of(16, 40))
            course.stops.map { it.visitOrder } shouldBe listOf(1, 2, 3)
            course.stops.map { it.nextTravelMin } shouldBe listOf(8, 12, null)
            course.stops.map { it.nextTravelM } shouldBe listOf(600, 900, null)
            course.stops[0].nextPath shouldBe path
            course.stops[2].nextPath.shouldBeNull()
            course.endTime shouldBe LocalTime.of(17, 50)
            course.totalMin shouldBe 230
            course.totalTravelM shouldBe 1500
            course.visitDate shouldBe visitDate
            course.startTime shouldBe LocalTime.of(14, 0)
            course.title shouldBe "성수 코스"
        }

        test("0 구간(같은 건물)은 이동 0분으로 바로 이어진다") {
            val legs = listOf(RouteLeg(0, 0, emptyList()), RouteLeg(300, 240, path))

            val course = scheduler.schedule(draft(30, 40, 50), legs, visitDate, LocalTime.of(11, 0))

            course.stops.map { it.visitAt } shouldBe listOf(LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(12, 14))
            course.stops[0].nextTravelMin shouldBe 0
            course.stops[0].nextPath shouldBe emptyList()
            course.endTime shouldBe LocalTime.of(13, 4)
            course.totalMin shouldBe 124
        }

        test("자정을 넘기면 IllegalStateException") {
            val legs = listOf(RouteLeg(600, 600, path))

            shouldThrow<IllegalStateException> { scheduler.schedule(draft(60, 60), legs, visitDate, LocalTime.of(22, 0)) }
        }
    })
