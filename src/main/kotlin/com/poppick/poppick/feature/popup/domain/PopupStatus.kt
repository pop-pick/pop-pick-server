package com.poppick.poppick.feature.popup.domain

/** 팝업 레코드의 노출 · 생명주기 상태. */
enum class PopupStatus {
    /** 수집 직후, 보강 · 검수 전. 기본값. 사용자에게 노출하지 않는다. */
    DRAFT,

    /** 보강을 마쳐 사용자에게 노출 가능한 운영 중(또는 예정) 팝업. */
    ACTIVE,

    /** 운영 종료일(end_date)이 지나 만료됨. */
    EXPIRED,

    /** 보강을 거쳤지만 필수 정보(기간 · 장소 등)가 부족해 노출할 수 없음. */
    INCOMPLETE,

    /** 팝업이 아니거나 부적합해 제외됨. 중복 판정 후보에서도 빠진다. */
    REJECTED,
}
