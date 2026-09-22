package com.poppick.poppick.feature.member.dataaccess.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "member_interest_category")
class MemberInterestCategoryEntity(
    @Column(nullable = false)
    var interestCategoryId: Int,
    @Column(nullable = false)
    var memberKey: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_interest_category_id")
    var id: Long? = null,
) {
    companion object {
        fun new(
            interestCategoryId: Int,
            memberKey: String,
        ) = MemberInterestCategoryEntity(
            interestCategoryId = interestCategoryId,
            memberKey = memberKey,
        )
    }
}
