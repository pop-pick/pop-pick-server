package com.poppick.poppick.feature.popuplist.business

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

            PopupListService(reader).findPopularPopups() shouldBe popups

            verify(exactly = 1) { reader.findPopups(null, LocalDate.now(KST), PopupSortType.POPULAR, top3Cursorable) }
        }

        test("노출 대상이 3개보다 적으면 있는 만큼만 돌려준다") {
            val popups = listOf(popup(2, 1), popup(1, 0))
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(popups, top3Cursorable, hasNext = false)
                }

            PopupListService(reader).findPopularPopups() shouldBe popups
        }

        test("노출 대상이 없으면 빈 목록") {
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(emptyList(), top3Cursorable, hasNext = false)
                }

            PopupListService(reader).findPopularPopups() shouldBe emptyList()
        }

        test("인기 팝업 개수는 3") {
            PopupListService.POPULAR_POPUP_COUNT shouldBe 3
        }
    })
