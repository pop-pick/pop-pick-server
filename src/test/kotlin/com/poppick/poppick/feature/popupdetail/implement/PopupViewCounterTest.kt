package com.poppick.poppick.feature.popupdetail.implement

import com.poppick.poppick.feature.popup.LogCapture
import com.poppick.poppick.feature.popup.implement.PopupViewCountWriter
import com.poppick.poppick.feature.popupdetail.dataaccess.PopupViewMarkStore
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.RedisConnectionFailureException

class PopupViewCounterTest :
    FunSpec({
        val popupId = 100L
        val viewer = PopupViewer.member("abc")

        fun counter(
            store: PopupViewMarkStore,
            writer: PopupViewCountWriter,
        ) = PopupViewCounter(store, writer)

        test("새 조회(메모 기록 성공)면 조회수를 1회 올리고 올린 값을 반환한다") {
            val store = mockk<PopupViewMarkStore> { every { markIfAbsent(popupId, viewer) } returns true }
            val writer = mockk<PopupViewCountWriter> { every { increase(popupId) } returns 42L }

            counter(store, writer).count(popupId, viewer) shouldBe 42L

            verify(exactly = 1) { writer.increase(popupId) }
        }

        test("최근 조회(메모 이미 있음)면 조회수를 올리지 않고 NULL") {
            val store = mockk<PopupViewMarkStore> { every { markIfAbsent(popupId, viewer) } returns false }
            val writer = mockk<PopupViewCountWriter>()

            counter(store, writer).count(popupId, viewer) shouldBe null

            verify(exactly = 0) { writer.increase(any()) }
        }

        test("중복 판정 중 예외가 나도 전파하지 않고, 조회수를 올리지 않으며 WARN 을 남긴다") {
            val store =
                mockk<PopupViewMarkStore> {
                    every { markIfAbsent(popupId, viewer) } throws RedisConnectionFailureException("down")
                }
            val writer = mockk<PopupViewCountWriter>()

            LogCapture(PopupViewCounter::class.java.name).use { capture ->
                counter(store, writer).count(popupId, viewer) shouldBe null

                capture.messages() shouldHaveSize 1
                capture.messages().first() shouldContain "중복 판정 실패"
            }
            verify(exactly = 0) { writer.increase(any()) }
        }

        test("DB 증가 중 예외가 나도 전파하지 않고 NULL 과 WARN") {
            val store = mockk<PopupViewMarkStore> { every { markIfAbsent(popupId, viewer) } returns true }
            val writer =
                mockk<PopupViewCountWriter> {
                    every { increase(popupId) } throws DataIntegrityViolationException("db error")
                }

            LogCapture(PopupViewCounter::class.java.name).use { capture ->
                counter(store, writer).count(popupId, viewer) shouldBe null

                capture.messages() shouldHaveSize 1
                capture.messages().first() shouldContain "조회수 증가 실패"
            }
        }

        test("팝업이 그 사이 삭제돼 증가 대상이 없으면 NULL") {
            val store = mockk<PopupViewMarkStore> { every { markIfAbsent(popupId, viewer) } returns true }
            val writer = mockk<PopupViewCountWriter> { every { increase(popupId) } returns null }

            counter(store, writer).count(popupId, viewer) shouldBe null
        }

        test("로그에 조회자 식별값(memberKey)을 남기지 않는다") {
            val store =
                mockk<PopupViewMarkStore> {
                    every { markIfAbsent(popupId, viewer) } throws RedisConnectionFailureException("down")
                }

            LogCapture(PopupViewCounter::class.java.name).use { capture ->
                counter(store, mockk()).count(popupId, viewer)

                capture.messages().none { it.contains("abc") } shouldBe true
            }
        }
    })
