package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class EnrichmentPromptTest :
    FunSpec({
        val popup =
            Popup(
                source = SourceType.KAKAO_MAP,
                title = "레노버 성수 팝업스토어",
                placeName = "레노버 성수 팝업스토어",
                addressRoad = "서울 성동구 연무장길 10",
                addressJibun = "서울 성동구 성수동2가 322-1",
                rawPayload = mapOf("category_name" to "전자제품서비스센터 > 레노버"),
            )

        test("input 은 날짜 · 장소명 · 주소 · 요청 네 줄만(브랜드 추정 · 카카오 분류 없음)") {
            EnrichmentPrompt.build(popup, LocalDate.of(2026, 9, 25)) shouldBe
                """
                오늘 날짜: 2026-09-25 (KST)
                장소명: 레노버 성수 팝업스토어
                주소: 서울 성동구 연무장길 10
                이 팝업스토어의 정보를 웹에서 찾아 JSON 으로 정리해줘.
                """.trimIndent()
        }

        test("도로명이 없으면 지번 주소") {
            EnrichmentPrompt.build(popup.copy(addressRoad = null), LocalDate.of(2026, 9, 25)).lines()[2] shouldBe
                "주소: 서울 성동구 성수동2가 322-1"
        }

        test("재보강(retry > 0)이면 검색 기간을 YEAR 로 넓힌다") {
            SearchRecency.of(popup) shouldBe SearchRecency.MONTH
            SearchRecency.of(popup.copy(enrichRetryCount = 1)) shouldBe SearchRecency.YEAR
        }
    })
