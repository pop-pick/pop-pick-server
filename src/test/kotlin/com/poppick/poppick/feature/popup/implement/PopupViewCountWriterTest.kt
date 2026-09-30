package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupViewCountRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

class PopupViewCountWriterTest :
    FunSpec({
        test("원자적 UPDATE 후 올린 뒤 값을 조회해 반환한다") {
            val repository =
                mockk<PopupViewCountRepository> {
                    every { increaseViewCount(100L) } returns 1
                    every { findViewCount(100L) } returns 42L
                }

            PopupViewCountWriter(repository).increase(100L) shouldBe 42L

            verifyOrder {
                repository.increaseViewCount(100L)
                repository.findViewCount(100L)
            }
        }

        test("갱신된 행이 없으면(팝업 없음) 조회하지 않고 NULL") {
            val repository = mockk<PopupViewCountRepository> { every { increaseViewCount(999L) } returns 0 }

            PopupViewCountWriter(repository).increase(999L) shouldBe null

            verify(exactly = 0) { repository.findViewCount(any()) }
        }
    })
