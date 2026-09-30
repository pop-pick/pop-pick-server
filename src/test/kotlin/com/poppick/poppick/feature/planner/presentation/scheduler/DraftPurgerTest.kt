package com.poppick.poppick.feature.planner.presentation.scheduler

import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class DraftPurgerTest :
    FunSpec({
        // 2026-09-30 10:15 KST
        val clock = Clock.fixed(Instant.parse("2026-09-30T01:15:00Z"), ZoneId.of("Asia/Seoul"))
        val olderThan = OffsetDateTime.parse("2026-09-29T10:15:00+09:00")

        test("created_at < 지금 - 24시간 기준을 넘긴다") {
            val writer = mockk<PlannerWriter>()
            every { writer.purgeDrafts(any()) } returns 2

            DraftPurger(writer, PlannerProperties(), clock).purge()

            verify(exactly = 1) { writer.purgeDrafts(olderThan) }
        }

        test("TTL 은 설정값을 쓴다") {
            val writer = mockk<PlannerWriter>()
            every { writer.purgeDrafts(any()) } returns 0

            DraftPurger(writer, PlannerProperties(draftTtlHours = 6), clock).purge()

            verify { writer.purgeDrafts(OffsetDateTime.parse("2026-09-30T04:15:00+09:00")) }
        }

        test("삭제가 실패해도 예외를 던지지 않는다(로그만)") {
            val writer = mockk<PlannerWriter>()
            every { writer.purgeDrafts(any()) } throws IllegalStateException("db down")

            DraftPurger(writer, PlannerProperties(), clock).purge()

            verify(exactly = 1) { writer.purgeDrafts(olderThan) }
        }
    })
