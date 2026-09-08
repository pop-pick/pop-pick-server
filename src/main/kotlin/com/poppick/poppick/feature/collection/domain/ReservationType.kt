package com.poppick.poppick.feature.collection.domain

/**
 * 팝업 입장 방식. "예약 오픈 알림" 대상 판별과 탐색 필터에 쓰인다.
 */
enum class ReservationType {
    /** 자유입장. 예약 · 대기 없이 방문 */
    NONE,

    /** 사전예약. 예약 페이지를 통해서만 입장 */
    RESERVATION,

    /** 현장대기. 방문해서 웨이팅을 등록 */
    WAITING,

    /** 사전예약과 현장대기를 함께 운영 */
    BOTH,

    /** 원천에서 입장 방식을 확인하지 못함. 보강(Perplexity) 대상 */
    UNKNOWN,
}
