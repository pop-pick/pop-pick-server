package com.poppick.poppick.feature.member.domain

data class OnboardingInfo(
    val accompanyType: AccompanyType,
    val numOfAccompany: Int,
    val interestCategoryIds: List<Int>,
    val favoriteAreaIds: List<Int>,
    val preferredActivityIds: List<Int>,
    val additionalInfo: String,
    val memberKey: String,
)
