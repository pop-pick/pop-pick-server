package com.poppick.poppick.feature.wish

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.wish.domain.PopupWish
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object WishFixtures {
    const val MEMBER_KEY = "member-1"

    val WISHED_AT: OffsetDateTime = OffsetDateTime.of(2026, 10, 2, 13, 40, 0, 0, ZoneOffset.ofHours(9))

    fun popup(
        id: Long,
        endDate: LocalDate? = LocalDate.of(2026, 10, 12),
    ) = Popup(
        id = id,
        source = SourceType.KAKAO_MAP,
        title = "팝업 $id",
        interestCategoryId = 3,
        imageUrls = listOf("https://img.example/$id-1.jpg", "https://img.example/$id-2.jpg"),
        startDate = LocalDate.of(2026, 9, 20),
        endDate = endDate,
        reservationType = ReservationType.UNKNOWN,
    )

    fun wish(
        id: Long,
        popupId: Long,
        memberKey: String = MEMBER_KEY,
        createdAt: OffsetDateTime = WISHED_AT,
    ) = PopupWish(id = id, memberKey = memberKey, popupId = popupId, createdAt = createdAt)
}
