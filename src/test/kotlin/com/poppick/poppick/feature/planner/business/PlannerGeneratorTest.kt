package com.poppick.poppick.feature.planner.business

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.domain.PreferredActivity
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.PreferredActivityReader
import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.domain.CandidateCondition
import com.poppick.poppick.feature.planner.domain.CandidatePopup
import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.CourseStop
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.planner.domain.PlannedCourse
import com.poppick.poppick.feature.planner.domain.PlannedStop
import com.poppick.poppick.feature.planner.domain.Planner
import com.poppick.poppick.feature.planner.domain.PlannerGenerateCommand
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.RouteFailedException
import com.poppick.poppick.feature.planner.implement.CandidatePopupFinder
import com.poppick.poppick.feature.planner.implement.CourseComposer
import com.poppick.poppick.feature.planner.implement.CoursePlanner
import com.poppick.poppick.feature.planner.implement.PlannerReader
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PlannerGeneratorTest :
    FunSpec({
        // 2026-09-28 19:40 KST
        val clock = Clock.fixed(Instant.parse("2026-09-28T10:40:00Z"), ZoneId.of("Asia/Seoul"))
        val today = LocalDate.of(2026, 9, 28)
        val memberKey = "member-1"

        fun popup(id: Long) =
            Popup(
                id = id,
                source = SourceType.KAKAO_MAP,
                title = "팝업$id",
                addressRoad = "서울 성동구 연무장길 $id",
                addressJibun = "서울 성동구 성수동2가 $id",
                latitude = 37.54,
                longitude = 127.05,
                imageUrls = listOf("https://img.example/$id.jpg"),
                openingHours = "매일 11:00~20:00",
            )

        val candidates = (1L..4L).map { CandidatePopup(popup(it), 0.1 * it) }
        val draft = CourseDraft("성수 코스", "소개", candidates.take(3).map { CourseStop(it.popup, 60, "이유${it.popup.id}") })
        val course =
            PlannedCourse(
                title = "성수 코스",
                summary = "소개",
                visitDate = LocalDate.of(2026, 10, 3),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(17, 20),
                totalMin = 200,
                totalTravelM = 1336,
                stops =
                    draft.stops.mapIndexed { index, stop ->
                        val last = index == draft.stops.lastIndex
                        PlannedStop(
                            popup = stop.popup,
                            visitOrder = index + 1,
                            visitAt = LocalTime.of(14, 0).plusMinutes(index * 70L),
                            stayMin = 60,
                            reason = stop.reason,
                            nextTravelMin = if (last) null else 10,
                            nextTravelM = if (last) null else 668,
                            nextPath = if (last) null else listOf(GeoPoint(37.54, 127.05)),
                        )
                    },
            )

        val command =
            PlannerGenerateCommand(
                areaId = 1,
                visitDate = LocalDate.of(2026, 10, 3),
                startTime = LocalTime.of(14, 0),
                accompanyType = AccompanyType.WITH_FRIEND,
                durationType = DurationType.HALF_DAY,
                interestCategoryIds = listOf(5, 1),
                preferredActivityIds = listOf(2),
                note = "향수 만들기",
            )

        class Fixture {
            val finder = mockk<CandidatePopupFinder>()
            val composer = mockk<CourseComposer>()
            val coursePlanner = mockk<CoursePlanner>()
            val reader = mockk<PlannerReader>()
            val writer = mockk<PlannerWriter>()
            val saved = slot<Planner>()
            val condition = slot<CandidateCondition>()
            val generator =
                PlannerGenerator(
                    favoriteAreaReader = mockk<FavoriteAreaReader> { every { findAll() } returns listOf(FavoriteArea(1, "성수")) },
                    interestCategoryReader =
                        mockk<InterestCategoryReader> {
                            every { findAll() } returns listOf(InterestCategory(1, "캐릭터/IP"), InterestCategory(5, "뷰티"))
                        },
                    preferredActivityReader =
                        mockk<PreferredActivityReader> { every { findAll() } returns listOf(PreferredActivity(2, "사진 찍기")) },
                    candidatePopupFinder = finder,
                    courseComposer = composer,
                    coursePlanner = coursePlanner,
                    plannerReader = reader,
                    plannerWriter = writer,
                    clock = clock,
                )

            init {
                every { reader.findScheduledPopupIds(memberKey) } returns setOf(1001L, 1002L)
                every { finder.find(capture(condition)) } returns candidates
                every { composer.compose(any(), candidates) } returns draft
                every { coursePlanner.plan(any(), draft) } returns course
                every { writer.saveDraft(capture(saved)) } answers { saved.captured.copy(id = 12) }
            }

            fun generate(command: PlannerGenerateCommand) = generator.generate(memberKey, command)
        }

        fun errorOf(block: () -> Unit) = shouldThrow<AppException>(block).errorType

        context("입력 검증") {
            test("방문일이 과거면 INVALID_VISIT_DATE") {
                errorOf { Fixture().generate(command.copy(visitDate = today.minusDays(1))) } shouldBe ErrorType.INVALID_VISIT_DATE
            }

            test("방문일이 31일 뒤면 INVALID_VISIT_DATE, 30일 뒤는 통과") {
                errorOf { Fixture().generate(command.copy(visitDate = today.plusDays(31))) } shouldBe ErrorType.INVALID_VISIT_DATE
                Fixture().generate(command.copy(visitDate = today.plusDays(30))).id shouldBe 12
            }

            test("시작 시각이 07:00 이면 INVALID_START_TIME") {
                errorOf { Fixture().generate(command.copy(startTime = LocalTime.of(7, 0))) } shouldBe ErrorType.INVALID_START_TIME
            }

            test("오늘 방문인데 현재(19:40) + 30분보다 이르면 INVALID_START_TIME") {
                errorOf { Fixture().generate(command.copy(visitDate = today, startTime = LocalTime.of(20, 0))) } shouldBe
                    ErrorType.INVALID_START_TIME
            }

            test("없는 지역 · 카테고리 · 활동 id 면 INVALID_REQUEST") {
                errorOf { Fixture().generate(command.copy(areaId = 99)) } shouldBe ErrorType.INVALID_REQUEST
                errorOf { Fixture().generate(command.copy(interestCategoryIds = listOf(1, 99))) } shouldBe ErrorType.INVALID_REQUEST
                errorOf { Fixture().generate(command.copy(preferredActivityIds = listOf(99))) } shouldBe ErrorType.INVALID_REQUEST
            }
        }

        test("후보가 minStops 미만이면 INSUFFICIENT_POPUPS 이고 CourseComposer 를 부르지 않는다") {
            val fixture = Fixture()
            every { fixture.finder.find(any()) } returns candidates.take(2)

            errorOf { fixture.generate(command) } shouldBe ErrorType.INSUFFICIENT_POPUPS
            verify(exactly = 0) { fixture.composer.compose(any(), any()) }
            verify(exactly = 0) { fixture.writer.saveDraft(any()) }
        }

        test("확정 일정의 팝업 id 를 excludePopupIds 로, 요청 id 를 이름으로 바꿔 넘긴다") {
            val fixture = Fixture()

            fixture.generate(command)

            fixture.condition.captured shouldBe
                CandidateCondition(
                    areaId = 1,
                    visitDate = command.visitDate,
                    categories = listOf("뷰티", "캐릭터/IP"),
                    activities = listOf("사진 찍기"),
                    note = "향수 만들기",
                    excludePopupIds = setOf(1001L, 1002L),
                )
        }

        test("정상 흐름: DRAFT 로 스냅샷 · endTime · totalMin 을 채워 저장한다") {
            val fixture = Fixture()

            val planner = fixture.generate(command)

            planner.id shouldBe 12
            with(fixture.saved.captured) {
                memberKey shouldBe "member-1"
                status shouldBe PlannerStatus.DRAFT
                areaId shouldBe 1
                endTime shouldBe LocalTime.of(17, 20)
                totalMin shouldBe 200
                totalTravelM shouldBe 1336
                requestNote shouldBe "향수 만들기"
                shareToken shouldBe null
                createdAt shouldBe java.time.OffsetDateTime.now(clock)
                stops.map { it.popupId } shouldBe listOf(1L, 2L, 3L)
                stops[0].title shouldBe "팝업1"
                stops[0].address shouldBe "서울 성동구 연무장길 1"
                stops[0].imageUrl shouldBe "https://img.example/1.jpg"
                stops[0].openingHours shouldBe "매일 11:00~20:00"
                stops[0].nextPath shouldBe listOf(GeoPoint(37.54, 127.05))
                stops[2].nextTravelMin shouldBe null
            }
        }

        test("RouteFailedException 은 그대로 전파하고 저장하지 않는다") {
            val fixture = Fixture()
            every { fixture.coursePlanner.plan(any(), any()) } throws RouteFailedException("경로 없음")

            shouldThrow<RouteFailedException> { fixture.generate(command) }
            verify(exactly = 0) { fixture.writer.saveDraft(any()) }
        }

        test("자정을 넘기는 코스(VisitScheduler IllegalStateException)는 INVALID_START_TIME") {
            val fixture = Fixture()
            every { fixture.coursePlanner.plan(any(), any()) } throws IllegalStateException("코스가 자정을 넘깁니다.")

            errorOf { fixture.generate(command) } shouldBe ErrorType.INVALID_START_TIME
            verify(exactly = 0) { fixture.writer.saveDraft(any()) }
        }

        context("confirm") {
            test("남의 플래너면 PLANNER_FORBIDDEN") {
                val fixture = Fixture()
                every { fixture.reader.get(12) } returns PlannerFixtures.planner(memberKey = "other")

                errorOf { fixture.generator.confirm(memberKey, 12) } shouldBe ErrorType.PLANNER_FORBIDDEN
            }

            test("이미 SCHEDULED 면 INVALID_PLANNER_STATUS") {
                val fixture = Fixture()
                every { fixture.reader.get(12) } returns PlannerFixtures.planner(status = PlannerStatus.SCHEDULED)

                errorOf { fixture.generator.confirm(memberKey, 12) } shouldBe ErrorType.INVALID_PLANNER_STATUS
            }

            test("방문일이 지난 DRAFT 면 INVALID_VISIT_DATE") {
                val fixture = Fixture()
                every { fixture.reader.get(12) } returns PlannerFixtures.planner(visitDate = today.minusDays(1))

                errorOf { fixture.generator.confirm(memberKey, 12) } shouldBe ErrorType.INVALID_VISIT_DATE
            }

            test("정상: PlannerWriter.confirm 을 현재 시각으로 부른다") {
                val fixture = Fixture()
                val scheduled = PlannerFixtures.planner(status = PlannerStatus.SCHEDULED)
                every { fixture.reader.get(12) } returns PlannerFixtures.planner()
                every { fixture.writer.confirm(12, java.time.OffsetDateTime.now(clock)) } returns scheduled

                fixture.generator.confirm(memberKey, 12) shouldBe scheduled
            }
        }
    })
