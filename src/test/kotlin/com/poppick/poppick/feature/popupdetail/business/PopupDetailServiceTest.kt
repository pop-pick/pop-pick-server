package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
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
        test("Reader 가 읽은 팝업을 그대로 돌려준다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }

            PopupDetailService(reader).findPopupDetail(1L) shouldBe popup

            verify(exactly = 1) { reader.read(1L) }
        }

        test("Reader 의 NOT_FOUND_DATA 예외는 그대로 전파한다") {
            val reader = mockk<PopupDetailReader> { every { read(999L) } throws AppException(ErrorType.NOT_FOUND_DATA) }

            val exception = shouldThrow<AppException> { PopupDetailService(reader).findPopupDetail(999L) }

            exception.errorType shouldBe ErrorType.NOT_FOUND_DATA
        }
    })
