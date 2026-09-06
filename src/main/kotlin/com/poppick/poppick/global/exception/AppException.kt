package com.poppick.poppick.global.exception

class AppException(
    val errorType: ErrorType,
    message: String? = null,
    cause: Throwable? = null,
    val errorData: Any? = null,
) : RuntimeException(message ?: errorType.message, cause)
