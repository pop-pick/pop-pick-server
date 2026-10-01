package com.poppick.poppick.feature.popup.domain

/** 벡터 검색 결과 1건. */
data class PopupSimilarity(
    val popupId: Long,
    /** pgvector 코사인 거리(`<=>`). 0 = 같은 방향, 2 = 반대. 쿼리 벡터 없이 검색했으면 NULL. */
    val distance: Double?,
)
