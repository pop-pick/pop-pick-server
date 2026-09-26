package com.poppick.poppick.feature.popup.implement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class PopupPurgerTest :
    FunSpec({
        val today = LocalDate.of(2026, 9, 26)

        test("대상이 없으면 삭제하지 않고 0/0") {
            val reader = mockk<PopupReader> { every { findExpiredPopupIds(today) } returns emptyList() }
            val writer = mockk<PopupWriter>()

            val report = PopupPurger(reader, writer).run(today)

            verify(exactly = 0) { writer.deleteAll(any()) }
            report.popups shouldBe 0
            report.embeddings shouldBe 0
        }

        test("대상 id 를 한 번에 넘겨 삭제하고 건수를 담는다") {
            val reader = mockk<PopupReader> { every { findExpiredPopupIds(today) } returns listOf(3L, 5L, 8L) }
            val writer = mockk<PopupWriter>()
            every { writer.deleteAll(listOf(3L, 5L, 8L)) } returns PopupWriter.DeleteResult(popups = 3, embeddings = 2)

            val report = PopupPurger(reader, writer).run(today)

            verify(exactly = 1) { writer.deleteAll(listOf(3L, 5L, 8L)) }
            report.popups shouldBe 3
            report.embeddings shouldBe 2
            report.summary().startsWith("purge: popups=3 embeddings=2 in ") shouldBe true
        }

        test("삭제 중 예외는 그대로 전파한다") {
            val reader = mockk<PopupReader> { every { findExpiredPopupIds(today) } returns listOf(1L) }
            val writer = mockk<PopupWriter>()
            every { writer.deleteAll(any()) } throws IllegalStateException("fk violation")

            shouldThrow<IllegalStateException> { PopupPurger(reader, writer).run(today) }
        }
    })
