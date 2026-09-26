package com.poppick.poppick.feature.popup.domain

/** 팝업 장소(place_id · 주소 · 좌표)를 얼마나 정확히 특정했는지. */
enum class PlaceResolution {
    /** 팝업 자체가 카카오 place 로 등록돼 있어 정확히 일치. */
    EXACT,

    /** 팝업이 열리는 건물 · 매장(venue) 수준으로만 특정. 예: 백화점, 복합몰. */
    VENUE,

    /** 장소를 특정하지 못함. */
    UNRESOLVED,
}
