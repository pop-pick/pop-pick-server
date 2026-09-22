package com.poppick.poppick.feature.member.dataaccess.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "member_preferred_activity")
class MemberPreferredActivityEntity(
    @Column(nullable = false)
    var preferredActivityId: Int,
    @Column(nullable = false)
    var memberKey: String,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_preferred_activity_id")
    var id: Long? = null,
) {
    companion object {
        fun new(
            preferredActivityId: Int,
            memberKey: String,
        ) = MemberPreferredActivityEntity(
            preferredActivityId = preferredActivityId,
            memberKey = memberKey,
        )
    }
}
