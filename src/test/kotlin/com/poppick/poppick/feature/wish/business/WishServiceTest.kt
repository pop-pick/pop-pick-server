package com.poppick.poppick.feature.wish.business

import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.feature.wish.implement.WishWriter
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

class WishServiceTest :
    FunSpec({
        val wishReader = mockk<WishReader>()
        val wishWriter = mockk<WishWriter>()
        val popupReader = mockk<PopupReader>()
        // 2026-10-02 00:30 KST(UTC 로는 전날). 오늘은 KST 기준이어야 한다.
        val now = ZonedDateTime.of(2026, 10, 2, 0, 30, 0, 0, KST)
        val clock = Clock.fixed(now.toInstant(), KST)
        val service = WishService(wishReader, wishWriter, popupReader, clock)

        beforeTest { clearAllMocks() }

        test("찜 등록은 팝업 존재를 확인하고 현재 시각으로 저장한다") {
            every { popupReader.findById(10) } returns WishFixtures.popup(10)
            every { wishWriter.add(MEMBER_KEY, 10, any()) } returns 1

            service.wish(MEMBER_KEY, 10)

            verify(exactly = 1) { wishWriter.add(MEMBER_KEY, 10, OffsetDateTime.now(clock)) }
        }

        test("이미 찜한 팝업(영향 행 0)이어도 예외 없이 끝난다") {
            every { popupReader.findById(10) } returns WishFixtures.popup(10)
            every { wishWriter.add(MEMBER_KEY, 10, any()) } returns 0

            service.wish(MEMBER_KEY, 10)
        }

        test("종료된 팝업도 찜할 수 있다") {
            every { popupReader.findById(10) } returns WishFixtures.popup(10, endDate = LocalDate.of(2026, 1, 1))
            every { wishWriter.add(MEMBER_KEY, 10, any()) } returns 1

            service.wish(MEMBER_KEY, 10)

            verify(exactly = 1) { wishWriter.add(MEMBER_KEY, 10, any()) }
        }

        test("없는 팝업은 NOT_FOUND_DATA 이고 저장하지 않는다") {
            every { popupReader.findById(999) } throws AppException(ErrorType.NOT_FOUND_DATA)

            shouldThrow<AppException> { service.wish(MEMBER_KEY, 999) }.errorType shouldBe ErrorType.NOT_FOUND_DATA

            verify(exactly = 0) { wishWriter.add(any(), any(), any()) }
        }

        test("찜 해제는 팝업 존재를 확인하지 않고 지운다(지울 행이 없어도 통과)") {
            every { wishWriter.remove(MEMBER_KEY, 999) } returns 0

            service.unwish(MEMBER_KEY, 999)

            verify(exactly = 1) { wishWriter.remove(MEMBER_KEY, 999) }
            verify(exactly = 0) { popupReader.findById(any()) }
        }

        test("내 찜 목록은 KST 오늘 날짜를 넘긴다") {
            val cursorable = Cursorable<Long>(null, 10)
            every { wishReader.findWishedPopups(MEMBER_KEY, LocalDate.of(2026, 10, 2), cursorable) } returns
                Slice(emptyList(), cursorable, false)

            service.findWishes(MEMBER_KEY, cursorable).content shouldBe emptyList()
        }
    })
