package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ImagePromptTest :
    FunSpec({
        val today = LocalDate.of(2026, 10, 3)
        val popup =
            Popup(
                id = 1,
                source = SourceType.KAKAO_MAP,
                title = "망그러진 곰 팝업스토어",
                brand = "망그러진 곰",
                placeName = "성수 캐릭터 팝업",
                addressRoad = "서울 성동구 연무장길 10",
                addressJibun = "서울 성동구 성수동2가 322-1",
                startDate = LocalDate.of(2026, 9, 10),
                endDate = LocalDate.of(2026, 10, 20),
            )

        test("팝업 정보를 줄 단위로 넣고 주소는 도로명을 쓴다") {
            ImagePrompt.build(popup, today) shouldBe
                """
                오늘 날짜: 2026-10-03
                팝업 이름: 망그러진 곰 팝업스토어
                브랜드: 망그러진 곰
                장소: 성수 캐릭터 팝업 (서울 성동구 연무장길 10)
                기간: 2026-09-10 ~ 2026-10-20
                """.trimIndent()
        }

        test("브랜드 · 기간이 없으면 미상, 도로명이 없으면 지번을 쓴다") {
            ImagePrompt.build(popup.copy(brand = null, startDate = null, endDate = null, addressRoad = " "), today) shouldBe
                """
                오늘 날짜: 2026-10-03
                팝업 이름: 망그러진 곰 팝업스토어
                브랜드: 미상
                장소: 성수 캐릭터 팝업 (서울 성동구 성수동2가 322-1)
                기간: 미상 ~ 미상
                """.trimIndent()
        }

        test("주소가 모두 없으면 장소명만, 장소명이 없으면 팝업 이름을 쓴다") {
            ImagePrompt.build(popup.copy(placeName = null, addressRoad = null, addressJibun = null), today).lines()[3] shouldBe
                "장소: 망그러진 곰 팝업스토어"
        }
    })
