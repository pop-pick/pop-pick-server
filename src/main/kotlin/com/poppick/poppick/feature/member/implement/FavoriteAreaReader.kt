package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.FavoriteAreaRepository
import com.poppick.poppick.feature.member.domain.FavoriteArea
import org.springframework.stereotype.Component

@Component
class FavoriteAreaReader(
    private val favoriteAreaRepository: FavoriteAreaRepository,
) {
    fun findAll(): List<FavoriteArea> = favoriteAreaRepository.findAll().map { it.toFavoriteArea() }
}
