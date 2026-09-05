package com.poppick.poppick.global.response

import com.poppick.poppick.global.exception.ErrorMessage
import com.poppick.poppick.global.exception.ErrorType

data class ApiResponse<T>(
    val resultType: ResultType,
    val data: T?,
    val error: ErrorMessage?,
) {
    companion object {
        fun <S> success(data: S? = null): ApiResponse<S> = ApiResponse(ResultType.SUCCESS, data, null)

        fun error(
            error: ErrorType,
            errorData: Any? = null,
        ): ApiResponse<Unit> = ApiResponse(ResultType.ERROR, null, ErrorMessage(error, errorData))
    }
}
