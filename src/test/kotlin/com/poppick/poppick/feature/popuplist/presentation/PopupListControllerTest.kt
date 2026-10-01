package com.poppick.poppick.feature.popuplist.presentation

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.presentation.PopupDetailController
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.domain.PopupListItem
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListPageResponse
import com.poppick.poppick.global.advice.GlobalExceptionHandler
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.CursorableArgumentResolver
import com.poppick.poppick.global.paging.Slice
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.Base64

/**
 * 목록 컨트롤러 + GlobalExceptionHandler + CursorableArgumentResolver 를 standalone 으로 띄워
 * sort · cursor 파라미터 처리와 nextCursor 를 검증한다. 보안 필터 체인 · DB 는 포함하지 않는다.
 */
class PopupListControllerTest :
    FunSpec({
        val service = mockk<PopupListService>()
        val mockMvc: MockMvc =
            MockMvcBuilders
                .standaloneSetup(PopupListController(service))
                .setControllerAdvice(GlobalExceptionHandler())
                .setCustomArgumentResolvers(CursorableArgumentResolver(), AuthenticationPrincipalArgumentResolver())
                .build()

        beforeEach {
            clearMocks(service)
            every { service.findCategoryNames() } returns mapOf(1 to "캐릭터/IP", 5 to "뷰티")
            every { service.findAreaNames() } returns mapOf(3 to "홍대")
        }

        val popups =
            listOf(
                Popup(
                    id = 1720,
                    source = SourceType.KAKAO_MAP,
                    title = "팝업 1720",
                    startDate = LocalDate.of(2026, 9, 25),
                    viewCount = 10,
                    interestCategoryId = 1,
                    areaId = 3,
                ),
                Popup(id = 1715, source = SourceType.KAKAO_MAP, title = "팝업 1715", startDate = LocalDate.of(2026, 9, 20), viewCount = 7),
            )

        val sort = slot<PopupSortType>()
        val cursorable = slot<Cursorable<PopupSearchCursor>>()

        fun stubService(hasNext: Boolean = true) {
            every { service.findPopups(any(), any(), any(), capture(sort), capture(cursorable)) } answers {
                Slice(popups.map { PopupListItem(it, wished = false) }, arg<Cursorable<PopupSearchCursor>>(4), hasNext)
            }
        }

        fun MockMvc.getJson(
            path: String,
            expectedStatus: Int,
        ): JsonNode {
            val body =
                get(path)
                    .andExpect { status { isEqualTo(expectedStatus) } }
                    .andReturn()
                    .response
                    .getContentAsString(Charsets.UTF_8)
            return Fixtures.jsonMapper.readTree(body)
        }

        fun decodeRaw(cursor: String) = String(Base64.getUrlDecoder().decode(cursor))

        test("sort 를 생략하면 최신순(LATEST)으로 조회하고 nextCursor 는 기존 최신순 형식이다") {
            stubService()

            val json = mockMvc.getJson("/api/v1/popups", 200)

            sort.captured shouldBe PopupSortType.LATEST
            cursorable.captured shouldBe Cursorable(null, 10)
            decodeRaw(json["data"]["nextCursor"].asString()) shouldBe "2026-09-20:1715"
        }

        test("sort=latest 는 최신순") {
            stubService()

            mockMvc.getJson("/api/v1/popups?sort=latest", 200)

            sort.captured shouldBe PopupSortType.LATEST
        }

        test("sort=popular 는 인기순이고 nextCursor 는 마지막 팝업의 P:{viewCount}:{popupId} 다") {
            stubService()

            val json = mockMvc.getJson("/api/v1/popups?sort=popular", 200)

            sort.captured shouldBe PopupSortType.POPULAR
            decodeRaw(json["data"]["nextCursor"].asString()) shouldBe "P:7:1715"
        }

        test("sort 는 대소문자를 구분하지 않는다") {
            stubService()

            mockMvc.getJson("/api/v1/popups?sort=POPULAR", 200)

            sort.captured shouldBe PopupSortType.POPULAR
        }

        test("areaId 를 지역 필터로 keyword · sort 와 함께 넘기고, 생략하면 null 이다") {
            stubService()

            mockMvc.getJson("/api/v1/popups?areaId=3&keyword=캐릭터&sort=popular", 200)
            mockMvc.getJson("/api/v1/popups", 200)

            verify(exactly = 1) { service.findPopups(null, "캐릭터", 3, PopupSortType.POPULAR, any()) }
            verify(exactly = 1) { service.findPopups(null, null, null, PopupSortType.LATEST, any()) }
        }

        test("areaId 가 숫자가 아니면 400 이고 조회하지 않는다") {
            mockMvc.getJson("/api/v1/popups?areaId=hongdae", 400)

            verify(exactly = 0) { service.findPopups(any(), any(), any(), any(), any()) }
        }

        test("목록 카드에 지역 id · 이름을 담고(없으면 null), 이름 조회는 요청당 1번이다") {
            stubService()

            val content = mockMvc.getJson("/api/v1/popups", 200)["data"]["content"].toList()

            content[0]["areaId"].asInt() shouldBe 3
            content[0]["areaName"].asString() shouldBe "홍대"
            content[1]["areaId"].isNull shouldBe true
            content[1]["areaName"].isNull shouldBe true
            verify(exactly = 1) { service.findAreaNames() }
        }

        test("목록 응답에는 viewCount 를 노출하지 않는다") {
            stubService()

            val item = mockMvc.getJson("/api/v1/popups?sort=popular", 200)["data"]["content"][0]

            item.has("viewCount") shouldBe false
        }

        test("알 수 없는 sort 는 400 · E400 이고 조회하지 않는다") {
            val json = mockMvc.getJson("/api/v1/popups?sort=views", 400)

            json["resultType"].asString() shouldBe "ERROR"
            json["error"]["errorCode"].asString() shouldBe "E400"
            verify(exactly = 0) { service.findPopups(any(), any(), any(), any(), any()) }
        }

        test("인기순 cursor 를 sort=popular 로 보내면 Popular cursor 로 이어서 조회한다") {
            stubService(hasNext = false)
            val cursor = PopupListPageResponse.encodeCursor(PopupSearchCursor.Popular(7, 1715))

            val json = mockMvc.getJson("/api/v1/popups?sort=popular&cursor=$cursor&limit=5", 200)

            sort.captured shouldBe PopupSortType.POPULAR
            cursorable.captured shouldBe Cursorable(PopupSearchCursor.Popular(7, 1715), 5)
            json["data"]["hasNext"].asBoolean() shouldBe false
            json["data"]["nextCursor"].isNull shouldBe true
        }

        test("최신순 cursor 를 sort=popular 로 보내면 400 이고 조회하지 않는다") {
            val latest = PopupListPageResponse.encodeCursor(PopupSearchCursor.Latest(LocalDate.of(2026, 9, 20), 1715))

            mockMvc.getJson("/api/v1/popups?sort=popular&cursor=$latest", 400)["error"]["errorCode"].asString() shouldBe "E400"
            verify(exactly = 0) { service.findPopups(any(), any(), any(), any(), any()) }
        }

        test("인기순 cursor 를 sort 없이(최신순) 보내면 400 이고 조회하지 않는다") {
            val popular = PopupListPageResponse.encodeCursor(PopupSearchCursor.Popular(7, 1715))

            mockMvc.getJson("/api/v1/popups?cursor=$popular", 400)["error"]["errorCode"].asString() shouldBe "E400"
            verify(exactly = 0) { service.findPopups(any(), any(), any(), any(), any()) }
        }

        test("keyword 와 sort=popular 를 함께 넘긴다") {
            stubService()

            mockMvc.getJson("/api/v1/popups?keyword=성수&sort=popular", 200)

            verify(exactly = 1) { service.findPopups(null, "성수", null, PopupSortType.POPULAR, any()) }
        }

        test("목록 카드에 카테고리 이름을 담고(없으면 null), 이름 조회는 요청당 1번이다") {
            stubService()

            val content = mockMvc.getJson("/api/v1/popups?sort=popular", 200)["data"]["content"].toList()

            content.map { it["interestCategoryId"].takeUnless { id -> id.isNull }?.asInt() } shouldBe listOf(1, null)
            content[0]["interestCategoryName"].asString() shouldBe "캐릭터/IP"
            content[1]["interestCategoryName"].isNull shouldBe true
            verify(exactly = 1) { service.findCategoryNames() }
        }

        test("limit 범위를 벗어나면 기존과 같이 400") {
            mockMvc.getJson("/api/v1/popups?sort=popular&limit=51", 400)["error"]["errorCode"].asString() shouldBe "E400"
        }

        context("GET /api/v1/popups/popular (지금 인기 있는 팝업)") {
            test("서비스가 준 순서 그대로 목록 카드 필드만 담은 배열을 내려준다(viewCount · cursor 없음)") {
                every { service.findPopularPopups(any()) } returns popups.map { PopupListItem(it, wished = false) }

                val json = mockMvc.getJson("/api/v1/popups/popular", 200)

                json["resultType"].asString() shouldBe "SUCCESS"
                json["data"].isArray shouldBe true
                json["data"].toList().map { it["popupId"].asLong() } shouldBe listOf(1720L, 1715L)
                json["data"][0].propertyNames().toSet() shouldBe
                    setOf(
                        "popupId",
                        "imageUrl",
                        "interestCategoryId",
                        "interestCategoryName",
                        "areaId",
                        "areaName",
                        "title",
                        "endDate",
                        "reservationType",
                        "wished",
                    )
                verify(exactly = 1) { service.findPopularPopups(null) }
                verify(exactly = 0) { service.findPopups(any(), any(), any(), any(), any()) }
            }

            test("인기 카드에도 카테고리 이름을 담고, 이름 조회는 요청당 1번이다") {
                every { service.findPopularPopups(any()) } returns popups.map { PopupListItem(it, wished = false) }

                val data = mockMvc.getJson("/api/v1/popups/popular", 200)["data"]

                data[0]["interestCategoryName"].asString() shouldBe "캐릭터/IP"
                data[1]["interestCategoryName"].isNull shouldBe true
                verify(exactly = 1) { service.findCategoryNames() }
            }

            test("노출 대상이 없으면 빈 배열") {
                every { service.findPopularPopups(any()) } returns emptyList()

                mockMvc.getJson("/api/v1/popups/popular", 200)["data"].size() shouldBe 0
            }

            test("쿼리 파라미터(sort · limit · cursor · keyword)는 무시하고 항상 인기 Top3 조회다") {
                every { service.findPopularPopups(any()) } returns popups.map { PopupListItem(it, wished = false) }

                mockMvc.getJson("/api/v1/popups/popular?sort=latest&limit=50&cursor=abc&keyword=x", 200)

                verify(exactly = 1) { service.findPopularPopups(null) }
            }

            test("상세 API(/{popupId})와 함께 떠 있어도 /popular 는 인기 API 로, 숫자 경로는 상세 API 로 간다") {
                val detailService = mockk<PopupDetailService>()
                every { service.findPopularPopups(any()) } returns popups.map { PopupListItem(it, wished = false) }
                every { detailService.findPopupDetail(any(), 1715L, any()) } returns PopupDetail(popups[1], wished = false)
                every { detailService.findCategoryNames() } returns emptyMap()
                every { detailService.findAreaNames() } returns emptyMap()
                val bothControllers =
                    MockMvcBuilders
                        .standaloneSetup(PopupListController(service), PopupDetailController(detailService))
                        .setControllerAdvice(GlobalExceptionHandler())
                        .setCustomArgumentResolvers(CursorableArgumentResolver(), AuthenticationPrincipalArgumentResolver())
                        .build()

                bothControllers.getJson("/api/v1/popups/popular", 200)["data"].size() shouldBe 2
                verify(exactly = 0) { detailService.findPopupDetail(any(), any(), any()) }

                bothControllers.getJson("/api/v1/popups/1715", 200)["data"]["popupId"].asLong() shouldBe 1715L
                verify(exactly = 1) { detailService.findPopupDetail(null, 1715L, any()) }
            }
        }

        context("GET /api/v1/popups/map (지도 팝업)") {
            val bounds = "swLat=37.50&swLng=126.95&neLat=37.60&neLng=127.10"
            val mapPopups =
                listOf(
                    popups[0].copy(latitude = 37.54, longitude = 127.05, areaId = 1),
                    popups[1].copy(latitude = 37.51, longitude = 126.97, areaId = null),
                )

            fun stubMap(result: List<Popup> = mapPopups) {
                every { service.findMapPopups(any(), any()) } returns result
                every { service.findAreaNames() } returns mapOf(1 to "성수")
            }

            test("영역 좌표와 keyword 를 넘기고, 마커 · 카드 필드 11개를 담은 배열을 내려준다") {
                stubMap()

                val data = mockMvc.getJson("/api/v1/popups/map?$bounds&keyword=성수", 200)["data"]

                verify(exactly = 1) { service.findMapPopups("성수", MapBounds(37.50, 126.95, 37.60, 127.10)) }
                data.toList().map { it["popupId"].asLong() } shouldBe listOf(1720L, 1715L)
                data[0].propertyNames().toSet() shouldBe
                    setOf(
                        "popupId",
                        "latitude",
                        "longitude",
                        "title",
                        "imageUrl",
                        "interestCategoryId",
                        "interestCategoryName",
                        "areaId",
                        "areaName",
                        "endDate",
                        "reservationType",
                    )
                data[0]["latitude"].asDouble() shouldBe 37.54
                data[0]["longitude"].asDouble() shouldBe 127.05
                data[0]["interestCategoryName"].asString() shouldBe "캐릭터/IP"
                data[0]["areaId"].asInt() shouldBe 1
                data[0]["areaName"].asString() shouldBe "성수"
                data[1]["interestCategoryName"].isNull shouldBe true
                data[1]["areaId"].isNull shouldBe true
                data[1]["areaName"].isNull shouldBe true
            }

            test("keyword 없이 호출할 수 있고, 카테고리 · 상권 이름 조회는 요청당 1번씩이다") {
                stubMap()

                mockMvc.getJson("/api/v1/popups/map?$bounds", 200)

                verify(exactly = 1) { service.findMapPopups(null, any()) }
                verify(exactly = 1) { service.findCategoryNames() }
                verify(exactly = 1) { service.findAreaNames() }
            }

            test("결과가 없으면 200 과 빈 배열") {
                stubMap(emptyList())

                mockMvc.getJson("/api/v1/popups/map?$bounds&keyword=없는검색어", 200)["data"].size() shouldBe 0
            }

            test("잘못된 영역은 400 · E400 이고 조회하지 않는다") {
                listOf(
                    "swLng=126.95&neLat=37.60&neLng=127.10", // swLat 누락
                    "", // 전부 누락
                    "swLat=abc&swLng=126.95&neLat=37.60&neLng=127.10", // 숫자 아님
                    "swLat=-91&swLng=126.95&neLat=37.60&neLng=127.10", // 위도 범위
                    "swLat=37.50&swLng=126.95&neLat=37.60&neLng=181", // 경도 범위
                    "swLat=37.60&swLng=126.95&neLat=37.50&neLng=127.10", // swLat > neLat
                    "swLat=37.50&swLng=127.10&neLat=37.60&neLng=126.95", // swLng > neLng
                ).forEach { query ->
                    mockMvc.getJson("/api/v1/popups/map?$query", 400)["error"]["errorCode"].asString() shouldBe "E400"
                }
                verify(exactly = 0) { service.findMapPopups(any(), any()) }
            }

            test("상세 API(/{popupId})와 함께 떠 있어도 /map 은 지도 API 로 가고 상세(조회수)는 호출되지 않는다") {
                stubMap()
                val detailService = mockk<PopupDetailService>()
                val bothControllers =
                    MockMvcBuilders
                        .standaloneSetup(PopupListController(service), PopupDetailController(detailService))
                        .setControllerAdvice(GlobalExceptionHandler())
                        .setCustomArgumentResolvers(CursorableArgumentResolver(), AuthenticationPrincipalArgumentResolver())
                        .build()

                bothControllers.getJson("/api/v1/popups/map?$bounds", 200)["data"].size() shouldBe 2
                verify(exactly = 0) { detailService.findPopupDetail(any(), any(), any()) }
            }
        }
    })
