package com.poppick.poppick.feature.auth.dataaccess.entity

import com.poppick.poppick.feature.auth.domain.OAuthMember
import com.poppick.poppick.feature.auth.domain.OAuthProvider
import com.poppick.poppick.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "oauth")
class OAuthEntity(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: OAuthProvider,

    @Column(nullable = false)
    var account: String,

    @Column(nullable = false)
    var memberKey: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun of(
            oAuthMember: OAuthMember,
            memberKey: String,
        ) = OAuthEntity(
            provider = oAuthMember.provider,
            account = oAuthMember.account,
            memberKey = memberKey,
        )
    }
}
