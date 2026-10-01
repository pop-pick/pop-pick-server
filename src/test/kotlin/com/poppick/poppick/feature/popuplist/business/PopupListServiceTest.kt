package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

class PopupListServiceTest :
    FunSpec({
        fun popup(
            id: Long,
            viewCount: Long,
        ) = Popup(id = id, source = SourceType.KAKAO_MAP, title = "팝업 $id", viewCount = viewCount)

        val top3Cursorable = Cursorable<PopupSearchCursor>(null, 3)

        test("인기 팝업은 목록 인기순(POPULAR) 첫 페이지를 keyword 없이 3개 조회한 결과다") {
            val popups = listOf(popup(3, 9), popup(2, 5), popup(1, 5))
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, any(), PopupSortType.POPULAR, top3Cursorable) } answers {
                        Slice(popups, top3Cursorable, hasNext = true)
                    }
                }

            PopupListService(reader, mockk(), mockk()).findPopularPopups() shouldBe popups

            verify(exactly = 1) { reader.findPopups(null, LocalDate.now(KST), PopupSortType.POPULAR, top3Cursorable) }
        }

        test("노출 대상이 3개보다 적으면 있는 만큼만 돌려준다") {
            val popups = listOf(popup(2, 1), popup(1, 0))
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(popups, top3Cursorable, hasNext = false)
                }

            PopupListService(reader, mockk(), mockk()).findPopularPopups() shouldBe popups
        }

        test("노출 대상이 없으면 빈 목록") {
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(emptyList(), top3Cursorable, hasNext = false)
                }

            PopupListService(reader, mockk(), mockk()).findPopularPopups() shouldBe emptyList()
        }

        test("인기 팝업 개수는 3") {
            PopupListService.POPULAR_POPUP_COUNT shouldBe 3
        }

        test("카테고리 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
            val categoryReader =
                mockk<InterestCategoryReader> {
                    every { findAll() } returns listOf(InterestCategory(1, "캐릭터/IP"), InterestCategory(5, "뷰티"))
                }

            PopupListService(mockk(), categoryReader, mockk()).findCategoryNames() shouldBe mapOf(1 to "캐릭터/IP", 5 to "뷰티")

            verify(exactly = 1) { categoryReader.findAll() }
        }

        context("지도 팝업") {
            val bounds = MapBounds(37.5, 126.95, 37.6, 127.1)

            test("상한 + 1 건을 오늘(KST) 기준으로 조회하고, 상한 이하면 그대로 돌려준다") {
                val popups = listOf(popup(2, 5), popup(1, 0))
                val reader =
                    mockk<PopupListReader> {
                        every { findMapPopups("성수", any(), bounds, PopupListService.MAP_POPUP_LIMIT + 1) } returns popups
                    }

                PopupListService(reader, mockk(), mockk()).findMapPopups("성수", bounds) shouldBe popups

                verify(exactly = 1) {
                    reader.findMapPopups("성수", LocalDate.now(KST), bounds, PopupListService.MAP_POPUP_LIMIT + 1)
                }
            }

            test("상한을 넘으면 상한 건수만 돌려준다(조회 순서 = 인기순 그대로)") {
                val over = (1..PopupListService.MAP_POPUP_LIMIT + 1).map { popup(it.toLong(), 0) }
                val reader = mockk<PopupListReader> { every { findMapPopups(null, any(), bounds, any()) } returns over }

                val result = PopupListService(reader, mockk(), mockk()).findMapPopups(null, bounds)

                result.size shouldBe PopupListService.MAP_POPUP_LIMIT
                result shouldBe over.take(PopupListService.MAP_POPUP_LIMIT)
            }

            test("결과가 없으면 빈 목록") {
                val reader = mockk<PopupListReader> { every { findMapPopups(any(), any(), bounds, any()) } returns emptyList() }

                PopupListService(reader, mockk(), mockk()).findMapPopups(null, bounds) shouldBe emptyList()
            }

            test("상권 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
                val areaReader =
                    mockk<FavoriteAreaReader> { every { findAll() } returns listOf(FavoriteArea(1, "성수"), FavoriteArea(7, "강남")) }

                PopupListService(mockk(), mockk(), areaReader).findAreaNames() shouldBe mapOf(1 to "성수", 7 to "강남")

                verify(exactly = 1) { areaReader.findAll() }
            }
        }
    })
