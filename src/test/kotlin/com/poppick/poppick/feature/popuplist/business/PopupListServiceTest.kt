package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.member.domain.FavoriteArea
import com.poppick.poppick.feature.member.domain.InterestCategory
import com.poppick.poppick.feature.member.domain.MemberPreference
import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.member.implement.MemberPreferenceReader
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.feature.wish.WishFixtures
import com.poppick.poppick.feature.wish.WishFixtures.MEMBER_KEY
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
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
                    every { findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, top3Cursorable) } answers {
                        Slice(popups, top3Cursorable, hasNext = true)
                    }
                }

            PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findPopularPopups(null).map { it.popup } shouldBe popups

            verify(exactly = 1) { reader.findPopups(null, emptyList(), null, LocalDate.now(KST), PopupSortType.POPULAR, top3Cursorable) }
        }

        test("노출 대상이 3개보다 적으면 있는 만큼만 돌려준다") {
            val popups = listOf(popup(2, 1), popup(1, 0))
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(popups, top3Cursorable, hasNext = false)
                }

            PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findPopularPopups(null).map { it.popup } shouldBe popups
        }

        test("노출 대상이 없으면 빈 목록") {
            val reader =
                mockk<PopupListReader> {
                    every { findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                        Slice(emptyList(), top3Cursorable, hasNext = false)
                }

            PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findPopularPopups(null) shouldBe emptyList()
        }

        test("인기 팝업 개수는 3") {
            PopupListService.POPULAR_POPUP_COUNT shouldBe 3
        }

        test("카테고리 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
            val categoryReader =
                mockk<InterestCategoryReader> {
                    every { findAll() } returns listOf(InterestCategory(1, "캐릭터/IP"), InterestCategory(5, "뷰티"))
                }

            PopupListService(mockk(), categoryReader, mockk(), mockk(), mockk()).findCategoryNames() shouldBe
                mapOf(1 to "캐릭터/IP", 5 to "뷰티")

            verify(exactly = 1) { categoryReader.findAll() }
        }

        context("지도 팝업") {
            val bounds = MapBounds(37.5, 126.95, 37.6, 127.1)

            test("상한 + 1 건을 오늘(KST) 기준으로 조회하고, 상한 이하면 그대로 돌려준다") {
                val popups = listOf(popup(2, 5), popup(1, 0))
                val reader =
                    mockk<PopupListReader> {
                        every { findMapPopups("성수", any(), any(), bounds, PopupListService.MAP_POPUP_LIMIT + 1) } returns popups
                    }
                val areaReader = mockk<FavoriteAreaReader> { every { findAll() } returns listOf(FavoriteArea(1, "성수")) }

                PopupListService(reader, mockk(), areaReader, mockk(), mockk()).findMapPopups("성수", bounds) shouldBe popups

                verify(exactly = 1) {
                    reader.findMapPopups("성수", listOf(1), LocalDate.now(KST), bounds, PopupListService.MAP_POPUP_LIMIT + 1)
                }
            }

            test("상한을 넘으면 상한 건수만 돌려준다(조회 순서 = 인기순 그대로)") {
                val over = (1..PopupListService.MAP_POPUP_LIMIT + 1).map { popup(it.toLong(), 0) }
                val reader = mockk<PopupListReader> { every { findMapPopups(null, emptyList(), any(), bounds, any()) } returns over }

                val result = PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findMapPopups(null, bounds)

                result.size shouldBe PopupListService.MAP_POPUP_LIMIT
                result shouldBe over.take(PopupListService.MAP_POPUP_LIMIT)
            }

            test("결과가 없으면 빈 목록") {
                val reader = mockk<PopupListReader> { every { findMapPopups(any(), any(), any(), bounds, any()) } returns emptyList() }

                PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findMapPopups(null, bounds) shouldBe emptyList()
            }

            test("상권 이름은 전체를 한 번만 조회해 id → 이름으로 돌려준다") {
                val areaReader =
                    mockk<FavoriteAreaReader> { every { findAll() } returns listOf(FavoriteArea(1, "성수"), FavoriteArea(7, "강남")) }

                PopupListService(mockk(), mockk(), areaReader, mockk(), mockk()).findAreaNames() shouldBe mapOf(1 to "성수", 7 to "강남")

                verify(exactly = 1) { areaReader.findAll() }
            }
        }

        context("회원 추천 팝업") {
            fun preference(
                categoryIds: List<Int> = emptyList(),
                areaIds: List<Int> = emptyList(),
                activityIds: List<Int> = emptyList(),
            ) = MemberPreference(favoriteAreaIds = areaIds, interestCategoryIds = categoryIds, preferredActivityIds = activityIds)

            fun preferenceReader(preference: MemberPreference) =
                mockk<MemberPreferenceReader> { every { find("member-1") } returns preference }

            fun reader(
                preferred: List<Popup> = emptyList(),
                popular: List<Popup> = emptyList(),
            ) = mockk<PopupListReader> {
                every { findPreferredPopups(any(), any(), any(), any()) } returns preferred
                every { findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                    Slice(popular, top3Cursorable, hasNext = false)
            }

            fun service(
                reader: PopupListReader,
                preference: MemberPreference,
                wishedIds: Set<Long> = emptySet(),
            ) = PopupListService(
                reader,
                mockk(),
                mockk(),
                preferenceReader(preference),
                mockk { every { findWishedPopupIds("member-1", any()) } returns wishedIds },
            )

            test("관심 카테고리 · 선호 지역을 오늘(KST) 기준으로 3개 조회하고, 3개면 인기 팝업은 조회하지 않는다") {
                val preferred = listOf(popup(9, 5), popup(7, 5), popup(3, 1))
                val reader = reader(preferred = preferred)

                service(reader, preference(categoryIds = listOf(1, 5), areaIds = listOf(2)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup } shouldBe preferred

                verify(exactly = 1) {
                    reader.findPreferredPopups(listOf(1, 5), listOf(2), LocalDate.now(KST), PopupListService.RECOMMENDED_POPUP_COUNT)
                }
                verify(exactly = 0) { reader.findPopups(any(), any(), any(), any(), any(), any()) }
            }

            test("선호 활동(preferredActivityIds)은 조건에 쓰지 않는다") {
                val reader = reader(preferred = listOf(popup(9, 0), popup(8, 0), popup(7, 0)))

                service(reader, preference(categoryIds = listOf(1), activityIds = listOf(3, 4))).findRecommendedPopups("member-1")

                verify(exactly = 1) { reader.findPreferredPopups(listOf(1), emptyList(), any(), any()) }
            }

            test("관심 카테고리만 있으면 선호 지역은 빈 목록으로 넘긴다") {
                val reader = reader(preferred = listOf(popup(9, 0), popup(8, 0), popup(7, 0)))

                service(reader, preference(categoryIds = listOf(2))).findRecommendedPopups("member-1")

                verify(exactly = 1) { reader.findPreferredPopups(listOf(2), emptyList(), any(), any()) }
            }

            test("선호 지역만 있으면 관심 카테고리는 빈 목록으로 넘긴다") {
                val reader = reader(preferred = listOf(popup(9, 0), popup(8, 0), popup(7, 0)))

                service(reader, preference(areaIds = listOf(4))).findRecommendedPopups("member-1")

                verify(exactly = 1) { reader.findPreferredPopups(emptyList(), listOf(4), any(), any()) }
            }

            test("선호값이 없으면 추천 쿼리 없이 인기 Top3 를 그대로 돌려준다") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))
                val reader = reader(popular = popular)

                service(reader, preference(activityIds = listOf(1))).findRecommendedPopups("member-1").map { it.popup } shouldBe popular

                verify(exactly = 0) { reader.findPreferredPopups(any(), any(), any(), any()) }
                verify(
                    exactly = 1,
                ) { reader.findPopups(null, emptyList(), null, LocalDate.now(KST), PopupSortType.POPULAR, top3Cursorable) }
            }

            test("일치 팝업이 없으면 인기 Top3") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))

                service(reader(popular = popular), preference(categoryIds = listOf(8)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup } shouldBe popular
            }

            test("일치 팝업이 1개면 인기 팝업 앞의 2개로 채운다") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))

                service(reader(preferred = listOf(popup(2, 0)), popular = popular), preference(areaIds = listOf(1)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup.id } shouldBe listOf(2L, 5L, 4L)
            }

            test("일치 팝업이 2개면 인기 팝업 1개로 채운다") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))

                service(reader(preferred = listOf(popup(3, 1), popup(2, 0)), popular = popular), preference(areaIds = listOf(1)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup.id } shouldBe listOf(3L, 2L, 5L)
            }

            test("채울 때 이미 담긴 팝업은 빼고 다음 인기 팝업으로 채운다") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))

                service(reader(preferred = listOf(popup(5, 9), popup(1, 0)), popular = popular), preference(categoryIds = listOf(1)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup.id } shouldBe listOf(5L, 1L, 4L)
            }

            test("노출 대상이 3개보다 적으면 있는 만큼만 돌려준다") {
                val popular = listOf(popup(5, 9), popup(4, 3))

                service(reader(preferred = listOf(popup(4, 3)), popular = popular), preference(categoryIds = listOf(1)))
                    .findRecommendedPopups("member-1")
                    .map { it.popup.id } shouldBe listOf(4L, 5L)
            }

            test("추천 결과(일치 3개)에 회원의 찜 여부를 붙인다") {
                val preferred = listOf(popup(9, 5), popup(7, 5), popup(3, 1))

                service(reader(preferred = preferred), preference(categoryIds = listOf(1)), wishedIds = setOf(7L))
                    .findRecommendedPopups("member-1")
                    .map { it.popup.id to it.wished } shouldBe listOf(9L to false, 7L to true, 3L to false)
            }

            test("인기 팝업으로 채운 추천 결과에도 찜 여부를 붙인다") {
                val popular = listOf(popup(5, 9), popup(4, 3), popup(1, 0))

                service(
                    reader(preferred = listOf(popup(2, 0)), popular = popular),
                    preference(areaIds = listOf(1)),
                    wishedIds = setOf(2L, 4L),
                ).findRecommendedPopups("member-1")
                    .map { it.popup.id to it.wished } shouldBe listOf(2L to true, 5L to false, 4L to true)
            }

            test("추천 팝업 개수는 3") {
                PopupListService.RECOMMENDED_POPUP_COUNT shouldBe 3
            }
        }

        context("지역(상권) 검색 · 필터") {
            val areas = listOf(FavoriteArea(1, "성수"), FavoriteArea(3, "홍대"), FavoriteArea(7, "강남"))
            val cursorable = Cursorable<PopupSearchCursor>(null, 10)

            fun listReader() =
                mockk<PopupListReader> {
                    every { findPopups(any(), any(), any(), any(), any(), any()) } answers {
                        Slice(emptyList(), cursorable, hasNext = false)
                    }
                    every { findMapPopups(any(), any(), any(), any(), any()) } returns emptyList()
                }

            test("keyword 와 이름이 부분 일치하는 상권 id 를 함께 넘긴다(홍 · 홍대 → 홍대, 앞뒤 공백 무시)") {
                listOf("홍대", "홍", " 홍대 ").forEach { keyword ->
                    val reader = listReader()
                    val areaReader = mockk<FavoriteAreaReader> { every { findAll() } returns areas }

                    PopupListService(
                        reader,
                        mockk(),
                        areaReader,
                        mockk(),
                        mockk(),
                    ).findPopups(null, keyword, null, PopupSortType.LATEST, cursorable)

                    verify(exactly = 1) {
                        reader.findPopups(keyword, listOf(3), null, LocalDate.now(KST), PopupSortType.LATEST, cursorable)
                    }
                    verify(exactly = 1) { areaReader.findAll() }
                }
            }

            test("상권 이름과 맞지 않는 keyword 면 상권 id 는 빈 목록") {
                val reader = listReader()
                val areaReader = mockk<FavoriteAreaReader> { every { findAll() } returns areas }

                PopupListService(
                    reader,
                    mockk(),
                    areaReader,
                    mockk(),
                    mockk(),
                ).findPopups(null, "캐릭터", null, PopupSortType.POPULAR, cursorable)

                verify(exactly = 1) { reader.findPopups("캐릭터", emptyList(), null, any(), PopupSortType.POPULAR, cursorable) }
            }

            test("keyword 가 없거나 공백이면 상권을 조회하지 않는다") {
                listOf(null, "", "  ").forEach { keyword ->
                    val reader = listReader()
                    val areaReader = mockk<FavoriteAreaReader>()

                    PopupListService(
                        reader,
                        mockk(),
                        areaReader,
                        mockk(),
                        mockk(),
                    ).findPopups(null, keyword, null, PopupSortType.LATEST, cursorable)

                    verify(exactly = 1) { reader.findPopups(keyword, emptyList(), null, any(), any(), any()) }
                    verify(exactly = 0) { areaReader.findAll() }
                }
            }

            test("areaId 지역 필터는 keyword 와 함께 그대로 넘긴다") {
                val reader = listReader()
                val areaReader = mockk<FavoriteAreaReader> { every { findAll() } returns areas }

                PopupListService(reader, mockk(), areaReader, mockk(), mockk()).findPopups(null, "캐릭터", 3, PopupSortType.LATEST, cursorable)
                PopupListService(reader, mockk(), areaReader, mockk(), mockk()).findPopups(null, null, 3, PopupSortType.POPULAR, cursorable)

                verify(exactly = 1) { reader.findPopups("캐릭터", emptyList(), 3, any(), PopupSortType.LATEST, cursorable) }
                verify(exactly = 1) { reader.findPopups(null, emptyList(), 3, any(), PopupSortType.POPULAR, cursorable) }
            }

            test("지도 keyword 도 목록과 같은 상권 이름 검색을 쓴다") {
                val reader = listReader()
                val areaReader = mockk<FavoriteAreaReader> { every { findAll() } returns areas }
                val bounds = MapBounds(37.5, 126.9, 37.6, 127.1)

                PopupListService(reader, mockk(), areaReader, mockk(), mockk()).findMapPopups("홍대", bounds)

                verify(exactly = 1) {
                    reader.findMapPopups("홍대", listOf(3), LocalDate.now(KST), bounds, PopupListService.MAP_POPUP_LIMIT + 1)
                }
            }

            test("인기 팝업은 keyword · 지역 조건 없이 조회한다") {
                val reader = listReader()

                PopupListService(reader, mockk(), mockk(), mockk(), mockk()).findPopularPopups(null)

                verify(exactly = 1) { reader.findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, any()) }
            }
        }

        context("찜 여부") {
            val popupListReader = mockk<PopupListReader>()
            val wishReader = mockk<WishReader>()
            val service = PopupListService(popupListReader, mockk(), mockk(), mockk(), wishReader)
            val cursorable = Cursorable<PopupSearchCursor>(null, 10)
            val page = listOf(WishFixtures.popup(3), WishFixtures.popup(2), WishFixtures.popup(1))

            beforeTest {
                clearAllMocks()
                every { popupListReader.findPopups(null, emptyList(), null, any(), PopupSortType.LATEST, cursorable) } returns
                    Slice(page, cursorable, true)
                every { popupListReader.findPopups(null, emptyList(), null, any(), PopupSortType.POPULAR, top3Cursorable) } returns
                    Slice(page, top3Cursorable, false)
            }

            test("비로그인이면 찜을 조회하지 않고 전부 wished = false") {
                val slice = service.findPopups(null, null, null, PopupSortType.LATEST, cursorable)

                slice.content.map { it.wished } shouldBe listOf(false, false, false)
                slice.hasNext shouldBe true
                verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
            }

            test("로그인 회원은 한 페이지 popupId 로 한 번 조회해 찜한 것만 wished = true") {
                every { wishReader.findWishedPopupIds(MEMBER_KEY, listOf(3L, 2L, 1L)) } returns setOf(2L)

                val slice = service.findPopups(MEMBER_KEY, null, null, PopupSortType.LATEST, cursorable)

                slice.content.map { it.popup.id to it.wished } shouldBe listOf(3L to false, 2L to true, 1L to false)
                verify(exactly = 1) { wishReader.findWishedPopupIds(any(), any()) }
            }

            test("인기 팝업도 비로그인이면 찜을 조회하지 않고 전부 wished = false") {
                service.findPopularPopups(null).map { it.wished } shouldBe listOf(false, false, false)

                verify(exactly = 0) { wishReader.findWishedPopupIds(any(), any()) }
            }

            test("인기 팝업도 로그인 회원은 한 번 조회해 찜한 것만 wished = true") {
                every { wishReader.findWishedPopupIds(MEMBER_KEY, listOf(3L, 2L, 1L)) } returns setOf(3L, 1L)

                service.findPopularPopups(MEMBER_KEY).map { it.popup.id to it.wished } shouldBe
                    listOf(3L to true, 2L to false, 1L to true)
                verify(exactly = 1) { wishReader.findWishedPopupIds(any(), any()) }
            }
        }
    })
