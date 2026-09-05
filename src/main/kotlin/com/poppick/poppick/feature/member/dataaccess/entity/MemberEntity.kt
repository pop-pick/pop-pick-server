package com.poppick.poppick.feature.member.dataaccess.entity

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.member.domain.NewMember
import com.poppick.poppick.global.entity.BaseEntity
import com.poppick.poppick.security.enums.MemberRole
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "member")
class MemberEntity(
    @Column(unique = true, nullable = false)
    var memberKey: String,

    @Column(nullable = true)
    var email: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = [JoinColumn(name = "member_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var roles: MutableSet<MemberRole> = mutableSetOf(MemberRole.ROLE_USER),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(member: NewMember) =
            MemberEntity(
                memberKey = UUID.randomUUID().toString(),
                email = member.emailValue,
                roles = mutableSetOf(MemberRole.ROLE_USER),
            )
    }

    fun toDomain() =
        Member.of(
            memberKey = memberKey,
            email = email,
            roles = roles.toSet(),
        )
}