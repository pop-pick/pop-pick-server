package com.poppick.poppick.feature.planner.business

import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.domain.PlannerListCursor
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import com.poppick.poppick.feature.planner.domain.PlannerSummary
import com.poppick.poppick.feature.planner.domain.PlannerSummaryPage
import com.poppick.poppick.feature.planner.implement.PlannerReader
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.feature.planner.implement.ShareTokenGenerator
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

class PlannerServiceTest :
    FunSpec({
        // 2026-09-28 19:40 KST
        val clock = Clock.fixed(Instant.parse("2026-09-28T10:40:00Z"), ZoneId.of("Asia/Seoul"))
        val today = LocalDate.of(2026, 9, 28)
        val memberKey = "member-1"

        class Fixture {
            val generator = mockk<PlannerGenerator>()
            val reader = mockk<PlannerReader>()
            val writer = mockk<PlannerWriter>(relaxed = true)
            val tokenGenerator = mockk<ShareTokenGenerator>()
            val service =
                PlannerService(
                    plannerGenerator = generator,
                    plannerReader = reader,
                    plannerWriter = writer,
                    shareTokenGenerator = tokenGenerator,
                    plannerProperties = PlannerProperties(shareBaseUrl = "https://pop-pick.app/share/"),
                    memberPreferenceReader = mockk(),
                    favoriteAreaReader = mockk<FavoriteAreaReader> { every { findAll() } returns listOf(FavoriteArea(1, "성수")) },
                    interestCategoryReader = mockk(),
                    preferredActivityReader = mockk(),
                    clock = clock,
                )

            fun owned(status: PlannerStatus) = every { generator.get(memberKey, 12) } returns PlannerFixtures.planner(status = status)
        }

        fun errorOf(block: () -> Unit) = shouldThrow<AppException>(block).errorType

        context("cancel") {
            test("SCHEDULED → PlannerWriter.cancel(현재 시각)") {
                val fixture = Fixture()
                fixture.owned(PlannerStatus.SCHEDULED)

                fixture.service.cancel(memberKey, 12)

                verify { fixture.writer.cancel(12, OffsetDateTime.now(clock)) }
                verify(exactly = 0) { fixture.writer.delete(any()) }
            }

            test("DRAFT → 물리 삭제") {
                val fixture = Fixture()
                fixture.owned(PlannerStatus.DRAFT)

                fixture.service.cancel(memberKey, 12)

                verify { fixture.writer.delete(12) }
                verify(exactly = 0) { fixture.writer.cancel(any(), any()) }
            }

            test("CANCELED → INVALID_PLANNER_STATUS(409)") {
                val fixture = Fixture()
                fixture.owned(PlannerStatus.CANCELED)

                errorOf { fixture.service.cancel(memberKey, 12) } shouldBe ErrorType.INVALID_PLANNER_STATUS
            }

            test("남의 것 → 소유자 검사(PlannerGenerator.get)의 PLANNER_FORBIDDEN 을 그대로") {
                val fixture = Fixture()
                every { fixture.generator.get(memberKey, 12) } throws AppException(ErrorType.PLANNER_FORBIDDEN)

                errorOf { fixture.service.cancel(memberKey, 12) } shouldBe ErrorType.PLANNER_FORBIDDEN
                verify(exactly = 0) { fixture.writer.cancel(any(), any()) }
                verify(exactly = 0) { fixture.writer.delete(any()) }
            }
        }

        context("share") {
            test("토큰이 이미 있으면 재사용하고 새로 만들지 않는다") {
                val fixture = Fixture()
                every { fixture.generator.get(memberKey, 12) } returns
                    PlannerFixtures.planner(status = PlannerStatus.SCHEDULED).copy(shareToken = "existing-token")

                val share = fixture.service.share(memberKey, 12)

                share.shareToken shouldBe "existing-token"
                share.shareUrl shouldBe "https://pop-pick.app/share/existing-token"
                verify(exactly = 0) { fixture.tokenGenerator.generate() }
                verify(exactly = 0) { fixture.writer.assignShareToken(any(), any()) }
            }

            test("없으면 발급해 저장한다") {
                val fixture = Fixture()
                fixture.owned(PlannerStatus.SCHEDULED)
                every { fixture.tokenGenerator.generate() } returns "new-token"
                every { fixture.writer.assignShareToken(12, "new-token") } returns "new-token"

                fixture.service.share(memberKey, 12).shareUrl shouldBe "https://pop-pick.app/share/new-token"
            }

            test("유일 인덱스 충돌이면 1회 재생성한다") {
                val fixture = Fixture()
                fixture.owned(PlannerStatus.SCHEDULED)
                every { fixture.tokenGenerator.generate() } returns "dup" andThen "fresh"
                every { fixture.writer.assignShareToken(12, "dup") } throws DataIntegrityViolationException("uq_planner_share_token")
                every { fixture.writer.assignShareToken(12, "fresh") } returns "fresh"

                fixture.service.share(memberKey, 12).shareToken shouldBe "fresh"
            }

            test("DRAFT · CANCELED 는 INVALID_PLANNER_STATUS") {
                listOf(PlannerStatus.DRAFT, PlannerStatus.CANCELED).forEach { status ->
                    val fixture = Fixture()
                    fixture.owned(status)

                    errorOf { fixture.service.share(memberKey, 12) } shouldBe ErrorType.INVALID_PLANNER_STATUS
                }
            }
        }

        context("getShared") {
            test("없는 토큰 → PLANNER_NOT_FOUND") {
                val fixture = Fixture()
                every { fixture.reader.findByShareToken("nope") } returns null

                errorOf { fixture.service.getShared("nope") } shouldBe ErrorType.PLANNER_NOT_FOUND
            }

            test("CANCELED → PLANNER_NOT_FOUND(취소하면 공유 링크도 죽는다)") {
                val fixture = Fixture()
                every { fixture.reader.findByShareToken("t") } returns PlannerFixtures.planner(status = PlannerStatus.CANCELED)

                errorOf { fixture.service.getShared("t") } shouldBe ErrorType.PLANNER_NOT_FOUND
                errorOf { fixture.service.sharedCalendarIcs("t") } shouldBe ErrorType.PLANNER_NOT_FOUND
            }

            test("SCHEDULED 면 지난 일정이어도 보여준다") {
                val fixture = Fixture()
                every { fixture.reader.findByShareToken("t") } returns
                    PlannerFixtures.planner(status = PlannerStatus.SCHEDULED, visitDate = today.minusDays(30))

                fixture.service.getShared("t").areaName shouldBe "성수"
            }
        }

        context("list") {
            fun summary(id: Long) =
                PlannerSummary(
                    id = id,
                    status = PlannerStatus.SCHEDULED,
                    title = "코스$id",
                    areaId = 1,
                    visitDate = LocalDate.of(2026, 10, 3),
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(17, 0),
                    totalMin = 180,
                    stopCount = 3,
                    firstStop = null,
                    canceledAt = null,
                )

            test("tab · 파싱한 커서 · size · 오늘(KST)을 reader 에 넘기고 지역 이름을 붙인다") {
                val fixture = Fixture()
                every { fixture.reader.list(any(), any(), any(), any(), any()) } returns
                    PlannerSummaryPage(listOf(summary(12)), false, null)

                val page = fixture.service.list(memberKey, PlannerListTab.UPCOMING, Cursorable("2026-10-01T09:30_7", 20))

                verify {
                    fixture.reader.list(
                        memberKey,
                        PlannerListTab.UPCOMING,
                        PlannerListCursor.Visit(LocalDate.of(2026, 10, 1), LocalTime.of(9, 30), 7),
                        20,
                        today,
                    )
                }
                page.content.single().areaName shouldBe "성수"
            }

            test("CANCELED 탭은 epoch ms 커서, 커서가 없으면 null") {
                val fixture = Fixture()
                every { fixture.reader.list(any(), any(), any(), any(), any()) } returns PlannerSummaryPage(emptyList(), false, null)

                fixture.service.list(memberKey, PlannerListTab.CANCELED, Cursorable("1790000000123_9", 10))
                fixture.service.list(memberKey, PlannerListTab.PAST, Cursorable(null, 10))

                verify {
                    fixture.reader.list(
                        memberKey,
                        PlannerListTab.CANCELED,
                        PlannerListCursor.Canceled(Instant.ofEpochMilli(1790000000123), 9),
                        10,
                        today,
                    )
                }
                verify { fixture.reader.list(memberKey, PlannerListTab.PAST, null, 10, today) }
            }

            test("tab 과 맞지 않거나 깨진 커서는 INVALID_REQUEST") {
                val fixture = Fixture()

                listOf(
                    PlannerListTab.UPCOMING to "1790000000123_9",
                    PlannerListTab.CANCELED to "2026-10-01T09:30_7",
                    PlannerListTab.PAST to "garbage",
                    PlannerListTab.PAST to "2026-10-01T09:30_",
                ).forEach { (tab, cursor) ->
                    errorOf { fixture.service.list(memberKey, tab, Cursorable(cursor, 20)) } shouldBe ErrorType.INVALID_REQUEST
                }
            }

            test("커서는 encode → parse 왕복이 같다") {
                val visit = PlannerListCursor.Visit(LocalDate.of(2026, 10, 3), LocalTime.of(14, 0), 12)
                val canceled = PlannerListCursor.Canceled(Instant.ofEpochMilli(1790000000123), 12)

                visit.encode() shouldBe "2026-10-03T14:00_12"
                PlannerListCursor.parse(PlannerListTab.UPCOMING, visit.encode()) shouldBe visit
                PlannerListCursor.parse(PlannerListTab.CANCELED, canceled.encode()) shouldBe canceled
            }
        }

        context("calendar") {
            test("공유 토큰이 있으면 본문 마지막 줄에 공유 URL") {
                val fixture = Fixture()
                every { fixture.generator.get(memberKey, 12) } returns
                    PlannerFixtures.planner(status = PlannerStatus.SCHEDULED).copy(shareToken = "tok")

                fixture.service.calendarIcs(memberKey, 12) shouldContain "POP PICK 에서 만든 코스 · https://pop-pick.app/share/tok"
                fixture.service.calendarIcs(memberKey, 12) shouldContain "DTSTAMP:20260928T104000Z"
            }
        }
    })
