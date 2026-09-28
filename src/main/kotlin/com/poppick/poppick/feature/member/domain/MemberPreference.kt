package com.poppick.poppick.feature.member.domain

/** 회원 온보딩 취향. 온보딩 전이면 둘 다 빈 리스트. */
data class MemberPreference(
    /** interest_category.category 이름들. */
    val interestCategories: List<String>,
    /** preferred_activity.activity 이름들. */
    val preferredActivities: List<String>,
)
