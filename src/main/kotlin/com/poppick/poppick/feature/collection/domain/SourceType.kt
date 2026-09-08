package com.poppick.poppick.feature.collection.domain

/**
 * 팝업 정보를 가져온 원천. 원본 보관(PopupSource)과 필드별 출처(Popup.fieldSources) 양쪽에서 쓰인다.
 */
enum class SourceType {
    /** 카카오맵 장소 검색. 좌표 · 주소 · 장소명이 정확한 편 */
    KAKAO_MAP,

    /** 서울 열린데이터 광장. 운영 기간 등 공공 데이터 */
    SEOUL_OPEN,

    /** Perplexity 검색 응답. 결손 필드 보강에 사용하며 원천 측 식별자가 없다 */
    PERPLEXITY,
}
