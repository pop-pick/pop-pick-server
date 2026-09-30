package com.poppick.poppick.feature.popupdetail.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import java.time.LocalDate
import java.time.OffsetDateTime

data class PopupDetailResponse(
    val popupId: Long,
    val title: String,
    val brand: String?,
    val description: String?,
    val imageUrls: List<String>?,
    val interestCategoryId: Int?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val openingHours: String?,
    val entryFee: Int?,
    val addressRoad: String?,
    val addressJibun: String?,
    val latitude: Double?,
    val longitude: Double?,
    val reservationType: ReservationType,
    val reservationUrl: String?,
    val reservationOpenAt: OffsetDateTime?,
    val source: SourceType,
    val sourceUrls: List<String>?,
    val tags: List<String>?,
    /** 팝픽 상세 조회수. 같은 조회자의 10분 내 재조회는 세지 않는다. */
    val viewCount: Long,
) {
    companion object {
        fun from(popup: Popup) =
            PopupDetailResponse(
                popupId = requireNotNull(popup.id),
                title = popup.title,
                brand = popup.brand,
                description = popup.description,
                imageUrls = popup.imageUrls,
                interestCategoryId = popup.interestCategoryId,
                startDate = popup.startDate,
                endDate = popup.endDate,
                openingHours = popup.openingHours,
                entryFee = popup.entryFee,
                addressRoad = popup.addressRoad,
                addressJibun = popup.addressJibun,
                latitude = popup.latitude,
                longitude = popup.longitude,
                reservationType = popup.reservationType,
                reservationUrl = popup.reservationUrl,
                reservationOpenAt = popup.reservationOpenAt,
                source = popup.source,
                sourceUrls = popup.sourceUrls,
                tags = popup.tags,
                viewCount = popup.viewCount,
            )
    }
}
