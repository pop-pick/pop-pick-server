package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.LocalDate

class EnrichmentPromptTest :
    FunSpec({
        val today = LocalDate.of(2026, 9, 26)
        val popup =
            Popup(
                source = SourceType.KAKAO_MAP,
                title = "레노버 성수 팝업스토어",
                placeId = "1001",
                placeName = "레노버 성수 팝업스토어",
                addressRoad = "서울 성동구 연무장길 10",
                addressJibun = "서울 성동구 성수동2가 322-1",
                sourceUrls = listOf("http://place.map.kakao.com/1001", "https://www.instagram.com/p/abc"),
                rawPayload = mapOf("category_name" to "전자제품서비스센터 > 레노버"),
            )

        test("카카오 장소 URL 을 참고로 넣고, 카카오 분류는 넣지 않는다") {
            val input = EnrichmentPrompt.build(popup, today)

            input shouldBe
                """
                오늘 날짜: 2026-09-26 (KST)
                장소명: 레노버 성수 팝업스토어
                주소: 서울 성동구 연무장길 10
                참고: http://place.map.kakao.com/1001
                이 팝업스토어의 정보를 웹에서 찾아 JSON 으로 정리해줘.
                """.trimIndent()
            input shouldNotContain "전자제품서비스센터"
        }

        test("sourceUrls 에 카카오 URL 이 없으면 placeId 로 조립한다") {
            EnrichmentPrompt.build(popup.copy(sourceUrls = null), today).lines()[3] shouldBe "참고: http://place.map.kakao.com/1001"
        }

        test("카카오 URL 도 placeId 도 없으면 참고 줄을 생략한다") {
            val input = EnrichmentPrompt.build(popup.copy(placeId = null, sourceUrls = listOf("https://www.instagram.com/p/abc")), today)

            input.lines().size shouldBe 4
            input shouldNotContain "참고:"
        }

        test("재시도(retry > 0)면 다시 찾으라는 문구로 바꾼다") {
            EnrichmentPrompt.build(popup.copy(enrichRetryCount = 1), today).lines().last() shouldBe
                "이전 검색에서 이 팝업의 기간을 확인하지 못했다. 다른 검색어와 출처로 다시 찾아 JSON 으로 정리해줘."
        }

        test("도로명이 없으면 지번 주소") {
            EnrichmentPrompt.build(popup.copy(addressRoad = null), today).lines()[2] shouldBe "주소: 서울 성동구 성수동2가 322-1"
        }

        test("첫 시도는 YEAR, 재시도는 최신성 필터 없음(NONE)") {
            SearchRecency.of(popup) shouldBe SearchRecency.YEAR
            SearchRecency.of(popup.copy(enrichRetryCount = 1)) shouldBe SearchRecency.NONE
            SearchRecency.NONE.value shouldBe null
        }
    })
