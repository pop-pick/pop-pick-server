package com.poppick.poppick.feature.auth.domain

import com.poppick.poppick.feature.member.domain.NewMember
import com.poppick.poppick.feature.member.domain.vo.Email

data class OAuthMember(
    val account: String,
    val provider: OAuthProvider,
    val email: Email,
) {
    companion object {
        fun of(
            account: String,
            provider: OAuthProvider,
            email: String,
        ) = OAuthMember(
            account = account,
            provider = provider,
            email = Email(email),
        )
    }

    fun toNewMember() =
        NewMember(
            email = email,
        )
}
