package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.InterestCategoryRepository
import com.poppick.poppick.feature.member.domain.InterestCategory
import org.springframework.stereotype.Component

@Component
class InterestCategoryReader(
    private val interestCategoryRepository: InterestCategoryRepository,
) {
    fun findAll(): List<InterestCategory> = interestCategoryRepository.findAll().map { it.toInterestCategory() }
}
