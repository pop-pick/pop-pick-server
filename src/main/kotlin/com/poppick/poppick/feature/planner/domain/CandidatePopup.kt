package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.popup.domain.Popup

/** 후보 검색 결과 1건. */
data class CandidatePopup(
    val popup: Popup,
    /** 쿼리와의 코사인 거리(0 = 같은 방향, 2 = 반대). 쿼리 텍스트 없이 검색했으면 NULL. */
    val distance: Double?,
)
