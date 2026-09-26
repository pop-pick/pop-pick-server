package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.FavoriteAreaRepository
import com.poppick.poppick.feature.member.dataaccess.repository.InterestCategoryRepository
import com.poppick.poppick.feature.member.dataaccess.repository.PreferredActivityRepository
import org.springframework.stereotype.Component

@Component
class OnboardingManager(
    private val favoriteAreaRepository: FavoriteAreaRepository,
    private val interestCategoryRepository: InterestCategoryRepository,
    private val preferredActivityRepository: PreferredActivityRepository,
) {
    fun findFavoriteAreas() = favoriteAreaRepository.findAll().map { it.toFavoriteArea() }

    fun findInterestCategories() = interestCategoryRepository.findAll().map { it.toInterestCategory() }

    fun findPreferredActivities() = preferredActivityRepository.findAll().map { it.toPreferredActivity() }
}
