package com.poppick.poppick.feature.member.presentation.dto.response

import com.poppick.poppick.feature.member.domain.FavoriteArea

data class FavoriteAreaResponse(
    val id: Int,
    val area: String,
) {
    companion object {
        fun from(favoriteArea: FavoriteArea) = FavoriteAreaResponse(favoriteArea.id, favoriteArea.area)
    }
}
