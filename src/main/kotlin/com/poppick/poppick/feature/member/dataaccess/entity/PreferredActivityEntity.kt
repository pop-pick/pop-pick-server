package com.poppick.poppick.feature.member.dataaccess.entity

import com.poppick.poppick.feature.member.domain.PreferredActivity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "preferred_activity")
class PreferredActivityEntity(
    @Column(nullable = false)
    var activity: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preferred_activity_id")
    var id: Int? = null,
) {
    fun toPreferredActivity() = PreferredActivity(id = id!!, activity = activity)
}
