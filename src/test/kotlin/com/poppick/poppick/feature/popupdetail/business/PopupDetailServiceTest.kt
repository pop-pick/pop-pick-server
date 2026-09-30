package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.popupdetail.implement.PopupViewCounter
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

class PopupDetailServiceTest :
    FunSpec({
        val viewer = PopupViewer.member("abc")

        test("Reader 가 읽은 팝업을 돌려주고, 새 조회면 올린 뒤 조회수를 담는다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L).copy(viewCount = 41L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val counter = mockk<PopupViewCounter> { every { count(1L, viewer) } returns 42L }

            PopupDetailService(reader, counter).findPopupDetail(1L, viewer) shouldBe popup.copy(viewCount = 42L)

            verifyOrder {
                reader.read(1L)
                counter.count(1L, viewer)
            }
        }

        test("중복 조회 · Redis/DB 오류로 counter 가 NULL 이면 읽은 조회수를 그대로 담는다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L).copy(viewCount = 41L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val counter = mockk<PopupViewCounter> { every { count(1L, viewer) } returns null }

            PopupDetailService(reader, counter).findPopupDetail(1L, viewer) shouldBe popup
        }

        test("Reader 의 NOT_FOUND_DATA 예외는 그대로 전파하고 조회수는 반영하지 않는다") {
            val reader = mockk<PopupDetailReader> { every { read(999L) } throws AppException(ErrorType.NOT_FOUND_DATA) }
            val counter = mockk<PopupViewCounter>()

            val exception = shouldThrow<AppException> { PopupDetailService(reader, counter).findPopupDetail(999L, viewer) }

            exception.errorType shouldBe ErrorType.NOT_FOUND_DATA
            verify(exactly = 0) { counter.count(any(), any()) }
        }
    })
