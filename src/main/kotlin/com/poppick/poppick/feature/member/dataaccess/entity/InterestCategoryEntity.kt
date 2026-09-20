package com.poppick.poppick.feature.member.dataaccess.entity

import com.poppick.poppick.feature.member.domain.InterestCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "interest_category")
class InterestCategoryEntity(
    @Column(nullable = false)
    var category: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interest_category_id")
    var id: Int? = null,
) {
    fun toInterestCategory() = InterestCategory(id = id!!, category = category)
}
