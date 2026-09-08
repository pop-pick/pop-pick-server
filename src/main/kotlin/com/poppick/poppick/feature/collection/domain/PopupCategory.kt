package com.poppick.poppick.feature.collection.domain

/**
 * 팝업 대분류. 탐색 필터와 임베딩 텍스트에 함께 쓰인다.
 * 현재 8개는 임의안이며, 기획안 필터 기준이 확정되면 조정한다.
 */
enum class PopupCategory {
    /** 패션 · 의류 */
    FASHION,

    /** 뷰티 · 코스메틱 */
    BEAUTY,

    /** 식음료 (Food & Beverage) */
    FNB,

    /** 캐릭터 · IP (포켓몬, 산리오 등) */
    CHARACTER_IP,

    /** 전시 · 아트 */
    ART,

    /** 테크 · 가전 · 게임 */
    TECH,

    /** 리빙 · 라이프스타일 */
    LIFESTYLE,

    /** 위 어디에도 속하지 않는 팝업 */
    ETC,
}
