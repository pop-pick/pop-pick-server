package com.poppick.poppick.feature.member.dataaccess.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "member_favorite_area")
class MemberFavoriteAreaEntity(
    @Column(nullable = false)
    var favoriteAreaId: Int,
    @Column(nullable = false)
    var memberKey: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_favorite_area_id")
    var id: Long? = null,
) {
    companion object {
        fun new(
            favoriteAreaId: Int,
            memberKey: String,
        ) = MemberFavoriteAreaEntity(
            favoriteAreaId = favoriteAreaId,
            memberKey = memberKey,
        )
    }
}
