package com.poppick.poppick.feature.member.domain

/** 회원 온보딩 선택값(id, 오름차순). 온보딩 전이면 전부 빈 리스트. 플래너 /form 기본값으로 쓴다. */
data class MemberPreference(
    /** favorite_area id. */
    val favoriteAreaIds: List<Int>,
    /** interest_category id. */
    val interestCategoryIds: List<Int>,
    /** preferred_activity id. */
    val preferredActivityIds: List<Int>,
)
