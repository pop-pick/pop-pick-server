package com.poppick.poppick.feature.popup.domain

/** 팝업 입장 방식. */
enum class ReservationType {
    /** 예약 · 웨이팅 없이 현장 자유 입장. */
    NONE,

    /** 사전 예약 필요. */
    RESERVATION,

    /** 현장 웨이팅(대기 등록). */
    WAITING,

    /** 사전 예약과 현장 웨이팅을 모두 운영. */
    BOTH,

    /** 입장 방식을 아직 확인하지 못함. 기본값. */
    UNKNOWN,
}
