package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import java.time.LocalDate

/** 지도 마커 + 하단 카드 1건. 이 응답만으로 카드를 그린다(카드 표시용으로 상세 API 를 호출하지 않는다). */
data class PopupMapResponse(
    val popupId: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val imageUrl: String?,
    val interestCategoryId: Int?,
    /** 카테고리 이름(뱃지 표시용). 카테고리가 없거나 알 수 없는 id 면 null. */
    val interestCategoryName: String?,
    val areaId: Int?,
    /** 상권 이름(지역 뱃지 표시용). 상권이 없거나 알 수 없는 id 면 null. */
    val areaName: String?,
    val endDate: LocalDate?,
    val reservationType: ReservationType,
) {
    companion object {
        /** categoryNames · areaNames 는 id → 이름. 좌표가 없는 팝업은 조회 단계에서 제외된다. */
        fun from(
            popup: Popup,
            categoryNames: Map<Int, String>,
            areaNames: Map<Int, String>,
        ) = PopupMapResponse(
            popupId = popup.id!!,
            latitude = popup.latitude!!,
            longitude = popup.longitude!!,
            title = popup.title,
            imageUrl = popup.imageUrls?.firstOrNull(),
            interestCategoryId = popup.interestCategoryId,
            interestCategoryName = popup.interestCategoryId?.let { categoryNames[it] },
            areaId = popup.areaId,
            areaName = popup.areaId?.let { areaNames[it] },
            endDate = popup.endDate,
            reservationType = popup.reservationType,
        )
    }
}
