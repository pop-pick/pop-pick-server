package com.poppick.poppick.feature.member.presentation.dto.response

import com.poppick.poppick.feature.member.domain.InterestCategory

data class InterestCategoryResponse(
    val id: Int,
    val category: String,
) {
    companion object {
        fun from(interestCategory: InterestCategory) = InterestCategoryResponse(interestCategory.id, interestCategory.category)
    }
}
