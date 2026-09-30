package com.poppick.poppick.feature.popupdetail.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popupdetail.PopupDetailFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val CATEGORY_NAMES = mapOf(1 to "캐릭터/IP", 3 to "F&B", 8 to "기타")

class PopupDetailResponseTest :
    FunSpec({
        test("팝업의 상세 필드를 모두 그대로 옮긴다") {
            val popup = PopupDetailFixtures.fullPopup(id = 1L)

            val response = PopupDetailResponse.from(popup, CATEGORY_NAMES)

            with(response) {
                popupId shouldBe 1L
                title shouldBe "망그러진 곰 팝업스토어"
                brand shouldBe "망그러진 곰"
                description shouldBe "망그러진 곰 굿즈와 포토존을 만날 수 있는 팝업"
                imageUrls shouldBe listOf("https://img.example.com/1.jpg", "https://img.example.com/2.jpg")
                interestCategoryId shouldBe 1
                interestCategoryName shouldBe "캐릭터/IP"
                startDate shouldBe LocalDate.of(2026, 9, 10)
                endDate shouldBe LocalDate.of(2026, 10, 12)
                openingHours shouldBe "매일 11:00~20:00, 월 휴무"
                entryFee shouldBe 5000
                addressRoad shouldBe "서울 성동구 연무장길 10"
                addressJibun shouldBe "서울 성동구 성수동2가 1"
                latitude shouldBe 37.54
                longitude shouldBe 127.05
                reservationType shouldBe ReservationType.RESERVATION
                reservationUrl shouldBe "https://booking.example.com/popup/1"
                reservationOpenAt shouldBe OffsetDateTime.of(2026, 9, 5, 14, 0, 0, 0, ZoneOffset.ofHours(9))
                source shouldBe SourceType.KAKAO_MAP
                sourceUrls shouldBe listOf("http://place.map.kakao.com/1001", "https://www.instagram.com/p/abc")
                tags shouldBe listOf("캐릭터", "굿즈", "포토존")
                viewCount shouldBe 0L
            }
        }

        test("카테고리가 없으면 이름도 null, 이름 목록에 없는 id 여도 이름은 null 이다(임의 값 없음)") {
            PopupDetailResponse
                .from(
                    PopupDetailFixtures.fullPopup().copy(interestCategoryId = null),
                    CATEGORY_NAMES,
                ).interestCategoryName shouldBe
                null
            with(PopupDetailResponse.from(PopupDetailFixtures.fullPopup().copy(interestCategoryId = 99), CATEGORY_NAMES)) {
                interestCategoryId shouldBe 99
                interestCategoryName shouldBe null
            }
        }

        test("조회수는 팝업의 viewCount 를 그대로 내려준다") {
            PopupDetailResponse.from(PopupDetailFixtures.fullPopup().copy(viewCount = 12_000L), CATEGORY_NAMES).viewCount shouldBe 12_000L
        }

        test("보강 전 팝업은 nullable 필드를 null 로, 입장 방식은 UNKNOWN 으로 내려준다") {
            val response = PopupDetailResponse.from(PopupDetailFixtures.minimalPopup(id = 2L), CATEGORY_NAMES)

            response shouldBe
                PopupDetailResponse(
                    popupId = 2L,
                    title = "이름만 있는 팝업",
                    brand = null,
                    description = null,
                    imageUrls = null,
                    interestCategoryId = null,
                    interestCategoryName = null,
                    startDate = null,
                    endDate = null,
                    openingHours = null,
                    entryFee = null,
                    addressRoad = null,
                    addressJibun = null,
                    latitude = null,
                    longitude = null,
                    reservationType = ReservationType.UNKNOWN,
                    reservationUrl = null,
                    reservationOpenAt = null,
                    source = SourceType.PERPLEXITY,
                    sourceUrls = null,
                    tags = null,
                    viewCount = 0L,
                )
        }

        test("일부만 채워진 값(종료일 · 도로명 주소 · 예약 오픈 시각 없음)은 있는 값만 채우고 나머지는 null 로 둔다") {
            val popup =
                PopupDetailFixtures.fullPopup().copy(
                    endDate = null,
                    addressRoad = null,
                    reservationType = ReservationType.WAITING,
                    reservationUrl = null,
                    reservationOpenAt = null,
                )

            val response = PopupDetailResponse.from(popup, CATEGORY_NAMES)

            response.startDate shouldBe LocalDate.of(2026, 9, 10)
            response.endDate shouldBe null
            response.addressRoad shouldBe null
            response.addressJibun shouldBe "서울 성동구 성수동2가 1"
            response.reservationType shouldBe ReservationType.WAITING
            response.reservationUrl shouldBe null
            response.reservationOpenAt shouldBe null
        }

        test("입장료 0(무료)은 null(미확인)과 구분해 0 으로 내려준다") {
            PopupDetailResponse.from(PopupDetailFixtures.fullPopup().copy(entryFee = 0), CATEGORY_NAMES).entryFee shouldBe 0
        }

        test("빈 목록은 null 로 바꾸지 않고 빈 목록 그대로 내려준다") {
            val popup = PopupDetailFixtures.fullPopup().copy(imageUrls = emptyList(), tags = emptyList())

            val response = PopupDetailResponse.from(popup, CATEGORY_NAMES)

            response.imageUrls shouldBe emptyList()
            response.tags shouldBe emptyList()
        }

        test("저장 전(id 없는) 팝업은 변환하지 않는다") {
            shouldThrow<IllegalArgumentException> {
                PopupDetailResponse.from(PopupDetailFixtures.fullPopup().copy(id = null), CATEGORY_NAMES)
            }
        }
    })
