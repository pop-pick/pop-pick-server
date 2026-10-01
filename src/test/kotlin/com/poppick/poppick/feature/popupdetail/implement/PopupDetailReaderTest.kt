package com.poppick.poppick.feature.popupdetail.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

class PopupDetailReaderTest :
    FunSpec({
        test("popupId 로 조회한 팝업을 도메인으로 돌려준다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L)
            val repository = mockk<PopupRepository> { every { findById(1L) } returns Optional.of(PopupDetailFixtures.entityOf(popup)) }

            PopupDetailReader(PopupReader(repository)).read(1L) shouldBe popup
        }

        test("nullable 필드가 비어 있는 팝업도 그대로 조회된다") {
            val popup = PopupDetailFixtures.minimalPopup(id = 2L)
            val repository = mockk<PopupRepository> { every { findById(2L) } returns Optional.of(PopupDetailFixtures.entityOf(popup)) }

            PopupDetailReader(PopupReader(repository)).read(2L) shouldBe popup
        }

        test("없는 popupId 는 NOT_FOUND_DATA 예외를 던진다") {
            val repository = mockk<PopupRepository> { every { findById(999L) } returns Optional.empty() }

            val exception = shouldThrow<AppException> { PopupDetailReader(PopupReader(repository)).read(999L) }

            exception.errorType shouldBe ErrorType.NOT_FOUND_DATA
        }
    })
