package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class PopupListResponse(
    val popupId: Long,
    val imageUrl: String?,
    val interestCategoryId: Int?,
    val title: String,
    val endDate: LocalDate?,
    val reservationType: ReservationType,
    @field:Schema(description = "로그인 회원의 찜 여부. 비로그인은 항상 false", example = "false")
    val wished: Boolean,
) {
    companion object {
        fun from(
            popup: Popup,
            wished: Boolean,
        ) = PopupListResponse(
            popupId = popup.id!!,
            imageUrl = popup.imageUrls?.firstOrNull(),
            interestCategoryId = popup.interestCategoryId,
            title = popup.title,
            endDate = popup.endDate,
            reservationType = popup.reservationType,
            wished = wished,
        )
    }
}
