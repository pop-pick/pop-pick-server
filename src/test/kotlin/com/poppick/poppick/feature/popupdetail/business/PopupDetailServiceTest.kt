package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.popupdetail.implement.PopupViewCounter
import com.poppick.poppick.feature.wish.implement.WishReader
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

        /** 조회수 변화가 없는(중복 조회) counter. 찜 테스트에서 조회수와 무관하게 쓴다. */
        fun duplicateCounter() = mockk<PopupViewCounter> { every { count(any(), any()) } returns null }

        test("Reader 가 읽은 팝업을 돌려주고, 새 조회면 올린 뒤 조회수를 담는다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L).copy(viewCount = 41L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val counter = mockk<PopupViewCounter> { every { count(1L, viewer) } returns 42L }

            PopupDetailService(reader, counter, mockk(), mockk(), mockk()).findPopupDetail(null, 1L, viewer) shouldBe
                PopupDetail(popup.copy(viewCount = 42L), wished = false)

            verifyOrder {
                reader.read(1L)
                counter.count(1L, viewer)
            }
        }

        test("중복 조회 · Redis/DB 오류로 counter 가 NULL 이면 읽은 조회수를 그대로 담는다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L).copy(viewCount = 41L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val counter = mockk<PopupViewCounter> { every { count(1L, viewer) } returns null }

            PopupDetailService(reader, counter, mockk(), mockk(), mockk()).findPopupDetail(null, 1L, viewer).popup shouldBe popup
        }

        test("새 조회로 올린 조회수와 찜 여부를 함께 담는다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L).copy(viewCount = 41L)
            val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
            val counter = mockk<PopupViewCounter> { every { count(1L, viewer) } returns 42L }
            val wishReader = mockk<WishReader> { every { findWishedPopupIds("member-1", listOf(1L)) } returns setOf(1L) }

            PopupDetailService(reader, counter, mockk(), mockk(), wishReader).findPopupDetail("member-1", 1L, viewer) shouldBe
                PopupDetail(popup.copy(viewCount = 42L), wished = true)
        }

        test("Reader 의 NOT_FOUND_DATA 예외는 그대로 전파하고 조회수 · 찜은 반영 · 조회하지 않는다") {
            val reader = mockk<PopupDetailReader> { every { read(999L) } throws AppException(ErrorType.NOT_FOUND_DATA) }
            val counter = mockk<PopupViewCounter>()
            val wishReader = mockk<WishReader>()

            val exception =
                shouldThrow<AppException> {
                    PopupDetailService(reader, counter, mockk(), mockk(), wishReader).findPopupDetail("member-1", 999L, viewer)
                }

            exception.errorType shouldBe ErrorType.NOT_FOUND_DATA
            verify(exactly = 0) { counter.count(any(), any()) }
            verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
        }

        test("카테고리 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
            val categoryReader =
                mockk<InterestCategoryReader> {
                    every { findAll() } returns listOf(InterestCategory(1, "캐릭터/IP"), InterestCategory(3, "F&B"))
                }

            PopupDetailService(mockk(), mockk(), categoryReader, mockk(), mockk()).findCategoryNames() shouldBe
                mapOf(1 to "캐릭터/IP", 3 to "F&B")

            verify(exactly = 1) { categoryReader.findAll() }
        }

        test("상권 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
            val areaReader =
                mockk<FavoriteAreaReader> {
                    every { findAll() } returns listOf(FavoriteArea(1, "성수"), FavoriteArea(3, "홍대"))
                }

            PopupDetailService(mockk(), mockk(), mockk(), areaReader, mockk()).findAreaNames() shouldBe mapOf(1 to "성수", 3 to "홍대")

            verify(exactly = 1) { areaReader.findAll() }
        }

        context("찜 여부") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L)

            test("비로그인이면 찜을 조회하지 않고 wished = false") {
                val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
                val wishReader = mockk<WishReader>()

                PopupDetailService(reader, duplicateCounter(), mockk(), mockk(), wishReader).findPopupDetail(null, 1L, viewer) shouldBe
                    PopupDetail(popup, wished = false)

                verify(exactly = 1) { reader.read(1L) }
                verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
            }

            test("로그인 회원이 찜한 팝업이면 wished = true") {
                val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
                val wishReader = mockk<WishReader> { every { findWishedPopupIds("member-1", listOf(1L)) } returns setOf(1L) }

                PopupDetailService(reader, duplicateCounter(), mockk(), mockk(), wishReader)
                    .findPopupDetail("member-1", 1L, viewer)
                    .wished shouldBe true
            }

            test("로그인 회원이 찜하지 않은 팝업이면 wished = false") {
                val reader = mockk<PopupDetailReader> { every { read(1L) } returns popup }
                val wishReader = mockk<WishReader> { every { findWishedPopupIds("member-1", listOf(1L)) } returns emptySet() }

                PopupDetailService(reader, duplicateCounter(), mockk(), mockk(), wishReader)
                    .findPopupDetail("member-1", 1L, viewer)
                    .wished shouldBe false
            }
        }
    })
