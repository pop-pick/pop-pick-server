package com.poppick.poppick.feature.member.domain

import com.poppick.poppick.feature.member.domain.vo.Email
import com.poppick.poppick.security.enums.MemberRole
import org.springframework.security.core.authority.SimpleGrantedAuthority

data class Member(
    val memberKey: String,
    val email: Email,
    val roles: Set<MemberRole>,
) {
    companion object {
        fun of(
            memberKey: String,
            email: String,
            roles: Set<MemberRole>,
        ) = Member(
            memberKey = memberKey,
            email = Email(email),
            roles = roles,
        )
    }

    val authorities: List<SimpleGrantedAuthority>
        get() = roles.map { SimpleGrantedAuthority(it.name) }
}
