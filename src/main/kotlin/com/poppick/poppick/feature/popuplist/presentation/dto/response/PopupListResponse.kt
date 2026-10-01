package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import java.time.LocalDate

data class PopupListResponse(
    val popupId: Long,
    val imageUrl: String?,
    val interestCategoryId: Int?,
    val title: String,
    val endDate: LocalDate?,
    val reservationType: ReservationType,
) {
    companion object {
        fun from(popup: Popup) =
            PopupListResponse(
                popupId = popup.id!!,
                imageUrl = popup.imageUrls?.firstOrNull(),
                interestCategoryId = popup.interestCategoryId,
                title = popup.title,
                endDate = popup.endDate,
                reservationType = popup.reservationType,
            )
    }
}
