package com.poppick.poppick.feature.popup.domain

/** 카카오맵 키워드 검색 결과 장소 1건. */
data class KakaoPlace(
    /** 카카오 place id. */
    val id: String,
    val placeName: String,
    /** 예: "가정,생활 > 팝업스토어" */
    val categoryName: String? = null,
    val categoryGroupCode: String? = null,
    val phone: String? = null,
    /** 지번 주소. 예: "서울 성동구 성수동2가 ..." */
    val addressJibun: String? = null,
    /** 도로명 주소. */
    val addressRoad: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val placeUrl: String? = null,
    /** 원문 document. popup.raw_payload 로 저장한다. */
    val raw: Map<String, Any?> = emptyMap(),
) {
    /** 서울 소재 여부. 지번이 비어 있으면 도로명으로 판단한다. */
    fun isInSeoul() = (addressJibun?.takeIf { it.isNotBlank() } ?: addressRoad).orEmpty().startsWith("서울")
}
