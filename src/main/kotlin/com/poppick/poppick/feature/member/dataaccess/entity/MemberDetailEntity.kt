package com.poppick.poppick.feature.member.dataaccess.entity

import com.poppick.poppick.feature.member.domain.AccompanyType
import com.poppick.poppick.feature.member.domain.OnboardingInfo
import com.poppick.poppick.global.entity.BaseEntity
import com.poppick.poppick.global.entity.EntityStatus
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "member_detail")
class MemberDetailEntity(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var accompanyType: AccompanyType,
    @Column(nullable = false)
    var numOfAccompany: Int,
    @Column(nullable = false)
    var additionalInfo: String,
    @Column(nullable = false)
    var memberKey: String,
    @Embedded
    var status: EntityStatus = EntityStatus(),
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_detail_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(onboardingInfo: OnboardingInfo) =
            MemberDetailEntity(
                accompanyType = onboardingInfo.accompanyType,
                numOfAccompany = onboardingInfo.numOfAccompany,
                additionalInfo = onboardingInfo.additionalInfo,
                memberKey = onboardingInfo.memberKey,
            )
    }
}
