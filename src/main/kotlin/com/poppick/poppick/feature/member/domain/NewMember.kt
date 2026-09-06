package com.poppick.poppick.feature.member.domain

import com.poppick.poppick.feature.member.domain.vo.Email

class NewMember(
    val email: Email,
) {
    companion object {
        fun of(email: String) =
            NewMember(
                email = Email(email),
            )
    }

    val emailValue: String
        get() = email.value
}
