package com.poppick.poppick.security.domain

import com.poppick.poppick.feature.member.domain.Member
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class AuthMember(
    val member: Member,
    private val attributes: Map<String, Any>,
    private val authorities: Collection<GrantedAuthority>,
) : OAuth2User {

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getName() = member.memberKey
}