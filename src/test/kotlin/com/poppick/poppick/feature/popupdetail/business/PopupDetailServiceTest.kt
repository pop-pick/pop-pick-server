package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PopupDetailServiceTest :
    FunSpec({
        val popup = PopupDetailFixtures.fullPopup(id = 1L)

        test("비로그인이면 찜을 조회하지 않고 wished = false") {
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val wishReader = mockk<WishReader>()

            PopupDetailService(reader, wishReader).findPopupDetail(null, 1L) shouldBe PopupDetail(popup, wished = false)

            verify(exactly = 1) { reader.read(1L) }
            verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
        }

        test("로그인 회원이 찜한 팝업이면 wished = true") {
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val wishReader = mockk<WishReader> { every { findWishedPopupIds("member-1", listOf(1L)) } returns setOf(1L) }

            PopupDetailService(reader, wishReader).findPopupDetail("member-1", 1L).wished shouldBe true
        }

        test("로그인 회원이 찜하지 않은 팝업이면 wished = false") {
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val wishReader = mockk<WishReader> { every { findWishedPopupIds("member-1", listOf(1L)) } returns emptySet() }

            PopupDetailService(reader, wishReader).findPopupDetail("member-1", 1L).wished shouldBe false
        }

        test("Reader 의 NOT_FOUND_DATA 예외는 그대로 전파하고 찜은 조회하지 않는다") {
            val reader = mockk<PopupDetailReader> { every { read(999L) } throws AppException(ErrorType.NOT_FOUND_DATA) }
            val wishReader = mockk<WishReader>()

            val exception = shouldThrow<AppException> { PopupDetailService(reader, wishReader).findPopupDetail("member-1", 999L) }

            exception.errorType shouldBe ErrorType.NOT_FOUND_DATA
            verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
        }
    })
