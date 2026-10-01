package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class PopupListResponse(
    val popupId: Long,
    val imageUrl: String?,
    val interestCategoryId: Int?,
    /** 카테고리 이름(뱃지 표시용). 카테고리가 없거나 알 수 없는 id 면 null. */
    val interestCategoryName: String?,
    val areaId: Int?,
    /** 상권 이름(지역 뱃지 표시용). 상권이 없거나 알 수 없는 id 면 null. */
    val areaName: String?,
    val title: String,
    val endDate: LocalDate?,
    val reservationType: ReservationType,
    @field:Schema(description = "로그인 회원의 찜 여부. 비로그인은 항상 false", example = "false")
    val wished: Boolean,
) {
    companion object {
        /** categoryNames · areaNames 는 id → 이름. */
        fun from(
            popup: Popup,
            wished: Boolean,
            categoryNames: Map<Int, String>,
            areaNames: Map<Int, String>,
        ) = PopupListResponse(
            popupId = popup.id!!,
            imageUrl = popup.imageUrls?.firstOrNull(),
            interestCategoryId = popup.interestCategoryId,
            interestCategoryName = popup.interestCategoryId?.let { categoryNames[it] },
            areaId = popup.areaId,
            areaName = popup.areaId?.let { areaNames[it] },
            title = popup.title,
            endDate = popup.endDate,
            reservationType = popup.reservationType,
            wished = wished,
        )
    }
}
