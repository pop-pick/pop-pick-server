package com.poppick.poppick.global.paging

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType

private const val MAX_PAGING_SIZE = 50

data class Cursorable<T>(
    val cursor: T?,
    val limit: Int,
) {
    init {
        if (limit !in 1..MAX_PAGING_SIZE) {
            throw AppException(ErrorType.INVALID_PAGING_SIZE)
        }
    }
}
