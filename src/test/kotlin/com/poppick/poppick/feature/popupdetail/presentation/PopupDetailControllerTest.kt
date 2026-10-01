package com.poppick.poppick.feature.popupdetail.presentation

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.global.advice.GlobalExceptionHandler
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.JsonNode

/** 값이 항상 채워지는 필드(non-null). */
private val NON_NULL_FIELDS = listOf("popupId", "title", "reservationType", "source", "wished")

/** 보강 전 등으로 비어 있을 수 있는 필드(nullable). */
private val NULLABLE_FIELDS =
    listOf(
        "brand",
        "description",
        "imageUrls",
        "interestCategoryId",
        "startDate",
        "endDate",
        "openingHours",
        "entryFee",
        "addressRoad",
        "addressJibun",
        "latitude",
        "longitude",
        "reservationUrl",
        "reservationOpenAt",
        "sourceUrls",
        "tags",
    )

private fun JsonNode.strings() = toList().map { it.asString() }

/**
 * 컨트롤러 + GlobalExceptionHandler 만 standalone 으로 띄워 응답 JSON 을 검증한다.
 * 보안 필터 체인 · DB 는 포함하지 않는다(인증 없음 = 비로그인). 로그인 여부별 wished 는 PopupWishedAuthTest 에서 본다.
 */
class PopupDetailControllerTest :
    FunSpec({
        val service = mockk<PopupDetailService>()
        val mockMvc: MockMvc =
            MockMvcBuilders
                .standaloneSetup(PopupDetailController(service))
                .setControllerAdvice(GlobalExceptionHandler())
                .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
                .build()

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

        test("GET /api/v1/popups/{popupId} 는 SUCCESS 와 상세 정보를 내려준다") {
            every { service.findPopupDetail(null, 1L) } returns PopupDetail(PopupDetailFixtures.fullPopup(id = 1L), wished = false)

            val json = mockMvc.getJson("/api/v1/popups/1", 200)

            json["resultType"].asString() shouldBe "SUCCESS"
            json["error"].isNull shouldBe true
            with(json["data"]) {
                this["popupId"].asLong() shouldBe 1L
                this["title"].asString() shouldBe "망그러진 곰 팝업스토어"
                this["brand"].asString() shouldBe "망그러진 곰"
                this["imageUrls"].strings() shouldBe
                    listOf("https://img.example.com/1.jpg", "https://img.example.com/2.jpg")
                this["interestCategoryId"].asInt() shouldBe 1
                this["startDate"].asString() shouldBe "2026-09-10"
                this["endDate"].asString() shouldBe "2026-10-12"
                this["openingHours"].asString() shouldBe "매일 11:00~20:00, 월 휴무"
                this["entryFee"].asInt() shouldBe 5000
                this["addressRoad"].asString() shouldBe "서울 성동구 연무장길 10"
                this["latitude"].asDouble() shouldBe 37.54
                this["longitude"].asDouble() shouldBe 127.05
                this["reservationType"].asString() shouldBe "RESERVATION"
                this["reservationUrl"].asString() shouldBe "https://booking.example.com/popup/1"
                this["reservationOpenAt"].isNull shouldBe false
                this["source"].asString() shouldBe "KAKAO_MAP"
                this["tags"].strings() shouldBe listOf("캐릭터", "굿즈", "포토존")
                this["wished"].asBoolean() shouldBe false
            }
        }

        test("응답 data 는 PopupDetailResponse 의 21개 필드만 담고 내부 필드는 노출하지 않는다") {
            every { service.findPopupDetail(null, 1L) } returns PopupDetail(PopupDetailFixtures.fullPopup(id = 1L), wished = false)

            val json = mockMvc.getJson("/api/v1/popups/1", 200)

            json["data"].propertyNames().toList() shouldContainExactlyInAnyOrder NON_NULL_FIELDS + NULLABLE_FIELDS
        }

        test("nullable 필드가 비어 있는 팝업도 200 으로 조회되고 해당 키는 null 로 내려간다") {
            every { service.findPopupDetail(null, 2L) } returns PopupDetail(PopupDetailFixtures.minimalPopup(id = 2L), wished = false)

            val json = mockMvc.getJson("/api/v1/popups/2", 200)

            with(json["data"]) {
                this["popupId"].asLong() shouldBe 2L
                this["title"].asString() shouldBe "이름만 있는 팝업"
                this["reservationType"].asString() shouldBe "UNKNOWN"
                this["source"].asString() shouldBe "PERPLEXITY"
                NULLABLE_FIELDS.forEach { field ->
                    has(field) shouldBe true
                    this[field].isNull shouldBe true
                }
            }
        }

        test("없는 popupId 는 404 · E404 에러 응답을 내려준다") {
            every { service.findPopupDetail(null, 999L) } throws AppException(ErrorType.NOT_FOUND_DATA)

            val json = mockMvc.getJson("/api/v1/popups/999", 404)

            json["resultType"].asString() shouldBe "ERROR"
            json["data"].isNull shouldBe true
            json["error"]["errorCode"].asString() shouldBe "E404"
            json["error"]["message"].asString() shouldBe "해당 데이터를 찾을 수 없습니다."
        }
    })
