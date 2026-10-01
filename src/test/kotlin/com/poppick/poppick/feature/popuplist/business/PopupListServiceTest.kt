package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PopupListServiceTest :
    FunSpec({
        val popupListReader = mockk<PopupListReader>()
        val wishReader = mockk<WishReader>()
        val service = PopupListService(popupListReader, wishReader)
        val cursorable = Cursorable<PopupSearchCursor>(null, 10)

        beforeTest {
            clearAllMocks()
            every { popupListReader.findPopups(null, any(), cursorable) } returns
                Slice(listOf(WishFixtures.popup(3), WishFixtures.popup(2), WishFixtures.popup(1)), cursorable, true)
        }

        test("비로그인이면 찜을 조회하지 않고 전부 wished = false") {
            val slice = service.findPopups(null, null, cursorable)

            slice.content.map { it.wished } shouldBe listOf(false, false, false)
            slice.hasNext shouldBe true
            verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
        }

        test("로그인 회원은 한 페이지 popupId 로 한 번 조회해 찜한 것만 wished = true") {
            every { wishReader.findWishedPopupIds(MEMBER_KEY, listOf(3L, 2L, 1L)) } returns setOf(2L)

            val slice = service.findPopups(MEMBER_KEY, null, cursorable)

            slice.content.map { it.popup.id to it.wished } shouldBe listOf(3L to false, 2L to true, 1L to false)
            verify(exactly = 1) { wishReader.findWishedPopupIds(any(), any()) }
        }
    })
