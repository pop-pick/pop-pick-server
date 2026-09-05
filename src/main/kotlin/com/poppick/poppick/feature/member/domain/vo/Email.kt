package com.poppick.poppick.feature.member.domain.vo

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType

@JvmInline
value class Email(
    val value: String,
) {
    companion object {
        private val EMAIL_PATTERN =
            "^(?![.])(?!.*[.]{2})[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$"
                .toRegex()
    }

    init {
        if (value.isBlank() || !EMAIL_PATTERN.matches(value)) {
            throw AppException(ErrorType.INVALID_EMAIL)
        }
    }
}
