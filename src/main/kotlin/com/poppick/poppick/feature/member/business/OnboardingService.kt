package com.poppick.poppick.feature.member.business

import com.poppick.poppick.feature.member.implement.OnboardingManager
import org.springframework.stereotype.Service

@Service
class OnboardingService(
    private val onboardingManager: OnboardingManager,
) {
    fun findFavoriteAreas() = onboardingManager.findFavoriteAreas()

    fun findInterestCategories() = onboardingManager.findInterestCategories()

    fun findPreferredActivities() = onboardingManager.findPreferredActivities()
}
