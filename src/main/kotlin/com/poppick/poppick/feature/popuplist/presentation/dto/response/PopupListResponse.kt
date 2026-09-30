package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import java.time.LocalDate

data class PopupListResponse(
    val popupId: Long,
    val imageUrl: String?,
    val interestCategoryId: Int?,
    /** 카테고리 이름(뱃지 표시용). 카테고리가 없거나 알 수 없는 id 면 null. */
    val interestCategoryName: String?,
    val title: String,
    val endDate: LocalDate?,
    val reservationType: ReservationType,
) {
    companion object {
        /** categoryNames 는 카테고리 id → 이름. */
        fun from(
            popup: Popup,
            categoryNames: Map<Int, String>,
        ) = PopupListResponse(
            popupId = popup.id!!,
            imageUrl = popup.imageUrls?.firstOrNull(),
            interestCategoryId = popup.interestCategoryId,
            interestCategoryName = popup.interestCategoryId?.let { categoryNames[it] },
            title = popup.title,
            endDate = popup.endDate,
            reservationType = popup.reservationType,
        )
    }
}
