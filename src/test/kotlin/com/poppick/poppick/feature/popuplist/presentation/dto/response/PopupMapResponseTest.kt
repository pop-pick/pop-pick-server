package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class PopupMapResponseTest :
    FunSpec({
        val categoryNames = mapOf(1 to "캐릭터/IP")
        val areaNames = mapOf(1 to "성수")

        fun popup(
            interestCategoryId: Int?,
            areaId: Int?,
        ) = Popup(
            id = 10L,
            source = SourceType.KAKAO_MAP,
            title = "망그러진 곰 팝업스토어",
            imageUrls = listOf("https://img.example.com/1.jpg", "https://img.example.com/2.jpg"),
            interestCategoryId = interestCategoryId,
            areaId = areaId,
            endDate = LocalDate.of(2026, 10, 12),
            reservationType = ReservationType.RESERVATION,
            latitude = 37.54,
            longitude = 127.05,
            viewCount = 99,
        )

        test("마커 · 하단 카드 필드를 옮기고 카테고리 · 상권 이름을 붙인다") {
            PopupMapResponse.from(popup(1, 1), categoryNames, areaNames) shouldBe
                PopupMapResponse(
                    popupId = 10L,
                    latitude = 37.54,
                    longitude = 127.05,
                    title = "망그러진 곰 팝업스토어",
                    imageUrl = "https://img.example.com/1.jpg",
                    interestCategoryId = 1,
                    interestCategoryName = "캐릭터/IP",
                    areaId = 1,
                    areaName = "성수",
                    endDate = LocalDate.of(2026, 10, 12),
                    reservationType = ReservationType.RESERVATION,
                )
        }

        test("카테고리 · 상권이 없으면 이름도 null") {
            with(PopupMapResponse.from(popup(null, null), categoryNames, areaNames)) {
                interestCategoryId shouldBe null
                interestCategoryName shouldBe null
                areaId shouldBe null
                areaName shouldBe null
            }
        }

        test("이름 목록에 없는 id 면 이름은 null(임의 값 없음)") {
            with(PopupMapResponse.from(popup(99, 99), categoryNames, areaNames)) {
                interestCategoryId shouldBe 99
                interestCategoryName shouldBe null
                areaId shouldBe 99
                areaName shouldBe null
            }
        }

        test("이미지가 없으면 imageUrl 은 null") {
            PopupMapResponse.from(popup(1, 1).copy(imageUrls = null), categoryNames, areaNames).imageUrl shouldBe null
        }
    })
