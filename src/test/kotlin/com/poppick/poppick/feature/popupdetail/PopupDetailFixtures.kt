package com.poppick.poppick.feature.popupdetail

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object PopupDetailFixtures {
    /** 보강까지 끝나 상세 필드가 모두 채워진 팝업. */
    fun fullPopup(id: Long = 1L) =
        Popup(
            id = id,
            source = SourceType.KAKAO_MAP,
            externalId = "1001",
            sourceUrls = listOf("http://place.map.kakao.com/1001", "https://www.instagram.com/p/abc"),
            rawPayload = mapOf("id" to "1001"),
            title = "망그러진 곰 팝업스토어",
            brand = "망그러진 곰",
            interestCategoryId = 1,
            description = "망그러진 곰 굿즈와 포토존을 만날 수 있는 팝업",
            tags = listOf("캐릭터", "굿즈", "포토존"),
            imageUrls = listOf("https://img.example.com/1.jpg", "https://img.example.com/2.jpg"),
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2026, 10, 12),
            openingHours = "매일 11:00~20:00, 월 휴무",
            reservationType = ReservationType.RESERVATION,
            reservationUrl = "https://booking.example.com/popup/1",
            reservationOpenAt = OffsetDateTime.of(2026, 9, 5, 14, 0, 0, 0, ZoneOffset.ofHours(9)),
            entryFee = 5000,
            placeId = "1001",
            placeName = "성수 캐릭터 팝업",
            addressRoad = "서울 성동구 연무장길 10",
            addressJibun = "서울 성동구 성수동2가 1",
            latitude = 37.54,
            longitude = 127.05,
            placeResolution = PlaceResolution.EXACT,
            enrichRetryCount = 0,
            enrichedAt = OffsetDateTime.of(2026, 9, 6, 5, 30, 0, 0, ZoneOffset.ofHours(9)),
        )

    /** 수집 직후(보강 전) 처럼 필수값(source · title · id) 외에는 비어 있는 팝업. */
    fun minimalPopup(id: Long = 2L) =
        Popup(
            id = id,
            source = SourceType.PERPLEXITY,
            title = "이름만 있는 팝업",
        )

    fun entityOf(popup: Popup) = PopupEntity.from(popup)
}
