package com.poppick.poppick.feature.popup.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.time.LocalDate

class PopupProfileTextTest :
    FunSpec({
        val full =
            Popup(
                id = 1,
                source = SourceType.KAKAO_MAP,
                title = "  망그러진 곰   팝업스토어 ",
                brand = "망그러진 곰",
                interestCategoryId = 1,
                description = "곰 굿즈 전시와\n  한정 판매",
                tags = listOf("캐릭터", " 굿즈 ", ""),
                imageUrls = listOf("https://img/1.jpg"),
                startDate = LocalDate.of(2026, 9, 10),
                endDate = LocalDate.of(2026, 10, 5),
                openingHours = "매일 11:00~20:00",
                reservationUrl = "https://booking/1",
                entryFee = 0,
                placeName = "성수 연무장",
                addressRoad = "서울 성동구 연무장길 12",
                addressJibun = "서울 성동구 성수동2가 1",
                latitude = 37.5,
                longitude = 127.0,
            )

        test("전 필드가 있으면 형식대로, 공백은 정리하고 날짜 · 운영시간 · 예약 · 입장료 · 좌표 · 전체 주소는 넣지 않는다") {
            PopupProfileText.build(full, "캐릭터/IP") shouldBe
                """
                망그러진 곰 팝업스토어
                카테고리: 캐릭터/IP · 브랜드: 망그러진 곰
                곰 굿즈 전시와 한정 판매
                체험 · 키워드: 캐릭터, 굿즈
                지역: 성동구 성수동
                """.trimIndent()
        }

        test("null · 빈 값 줄은 생략하고 지번이 없으면 도로명의 구만 쓴다") {
            val popup =
                full.copy(
                    brand = null,
                    description = "  ",
                    tags = emptyList(),
                    addressJibun = null,
                )

            PopupProfileText.build(popup, null) shouldBe
                """
                망그러진 곰 팝업스토어
                지역: 성동구
                """.trimIndent()
        }

        test("카테고리만 있으면 카테고리: … 만 남긴다") {
            PopupProfileText.build(full.copy(brand = ""), "캐릭터/IP").lines()[1] shouldBe "카테고리: 캐릭터/IP"
        }

        test("동 이름 끝의 숫자만 있어도 뗀다") {
            PopupProfileText
                .build(full.copy(addressJibun = "서울 마포구 서교동 1"), null)
                .lines()
                .last() shouldBe "지역: 마포구 서교동"
            PopupProfileText
                .build(full.copy(addressJibun = "서울 중구 을지로3가 5"), null)
                .lines()
                .last() shouldBe "지역: 중구 을지로"
        }

        test("title 뿐이어도 그대로 쓴다") {
            PopupProfileText.build(Popup(source = SourceType.KAKAO_MAP, title = "팝업"), null) shouldBe "팝업"
        }

        test("해시는 같은 입력에 같고 다른 입력에 다르다(SHA-256 hex)") {
            val text = PopupProfileText.build(full, "캐릭터/IP")

            PopupProfileText.sha256(text) shouldBe PopupProfileText.sha256(text)
            PopupProfileText.sha256(text) shouldNotBe PopupProfileText.sha256("$text ")
            PopupProfileText.sha256(text) shouldMatch Regex("[0-9a-f]{64}")
            PopupProfileText.sha256("abc") shouldBe "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }
    })
