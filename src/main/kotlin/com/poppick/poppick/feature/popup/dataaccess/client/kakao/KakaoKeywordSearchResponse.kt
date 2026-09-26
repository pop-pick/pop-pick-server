package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.feature.popup.domain.KakaoPlace

/** GET /v2/local/search/keyword.json 응답. document 는 원문 보관을 위해 Map 으로 받는다. */
data class KakaoKeywordSearchResponse(
    val meta: Meta,
    val documents: List<Map<String, Any?>> = emptyList(),
) {
    data class Meta(
        val isEnd: Boolean,
        val totalCount: Int? = null,
        val pageableCount: Int? = null,
    )
}

data class KakaoPlaceDocument(
    val id: String,
    val placeName: String,
    val categoryName: String? = null,
    val categoryGroupCode: String? = null,
    val phone: String? = null,
    /** 지번 주소. */
    val addressName: String? = null,
    /** 도로명 주소. */
    val roadAddressName: String? = null,
    /** 경도. */
    val x: String? = null,
    /** 위도. */
    val y: String? = null,
    val placeUrl: String? = null,
) {
    fun toDomain(raw: Map<String, Any?>) =
        KakaoPlace(
            id = id,
            placeName = placeName,
            categoryName = categoryName.nullIfBlank(),
            categoryGroupCode = categoryGroupCode.nullIfBlank(),
            phone = phone.nullIfBlank(),
            addressJibun = addressName.nullIfBlank(),
            addressRoad = roadAddressName.nullIfBlank(),
            latitude = y?.toDoubleOrNull(),
            longitude = x?.toDoubleOrNull(),
            placeUrl = placeUrl.nullIfBlank(),
            raw = raw,
        )

    // 카카오는 값이 없을 때 null 대신 "" 를 준다.
    private fun String?.nullIfBlank() = this?.takeIf { it.isNotBlank() }
}
