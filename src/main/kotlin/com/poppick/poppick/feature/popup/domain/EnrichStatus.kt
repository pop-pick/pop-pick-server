package com.poppick.poppick.feature.popup.domain

/** 마지막 보강(외부 검색으로 상세 정보 채우기) 시도의 결과. 보강 전에는 NULL. */
enum class EnrichStatus {
    /** 보강 성공. 상세 정보를 채움. */
    ENRICHED,

    /** 검색했지만 해당 팝업의 정보를 찾지 못함. 재시도 대상. */
    NOT_FOUND,

    /** 검색 결과 팝업스토어가 아닌 것으로 판정됨. */
    NOT_A_POPUP,
}
