package com.poppick.poppick.feature.member.dataaccess.entity

import com.poppick.poppick.feature.member.domain.FavoriteArea
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "favorite_area")
class FavoriteAreaEntity(
    @Column(nullable = false)
    var area: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_area_id")
    var id: Int? = null,
) {
    fun toFavoriteArea() = FavoriteArea(id = id!!, area = area)
}
