package com.poppick.poppick.feature.member.presentation.dto.request

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.domain.OnboardingInfo
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.UniqueElements

data class OnboardingRegisterRequest(
    val accompanyType: AccompanyType,
    @field:Min(value = 0)
    @field:Max(value = 4)
    val numOfAccompany: Int,
    @field:Size(min = 0)
    @field:UniqueElements
    val interestCategoryIds: List<Int>,
    @field:Size(min = 0)
    @field:UniqueElements
    val favoriteAreaIds: List<Int>,
    @field:Size(min = 0)
    @field:UniqueElements
    val preferredActivityIds: List<Int>,
    @field:Size(max = 200)
    val additionalInfo: String,
) {
    fun toOnboardingInfo(memberKey: String) =
        OnboardingInfo(
            accompanyType = accompanyType,
            numOfAccompany = numOfAccompany,
            interestCategoryIds = interestCategoryIds,
            favoriteAreaIds = favoriteAreaIds,
            preferredActivityIds = preferredActivityIds,
            additionalInfo = additionalInfo,
            memberKey = memberKey,
        )
}
