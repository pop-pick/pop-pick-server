package com.poppick.poppick.feature.planner.domain

enum class PlannerStatus {
    /** 생성 직후. 확정 전이라 목록에 보이지 않고, 오래되면 정리된다. */
    DRAFT,

    /** 사용자가 확정한 일정. 이 일정의 팝업은 다음 추천에서 뺀다. */
    SCHEDULED,

    /** 취소한 일정. 팝업은 다시 추천될 수 있다. */
    CANCELED,
}
