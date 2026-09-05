package com.poppick.poppick.global.paging

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.core.MethodParameter
import org.springframework.core.ResolvableType
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

private const val CURSOR = "cursor"
private const val LIMIT = "limit"

@Component
class CursorableArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == Cursorable::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val annotation = parameter.getParameterAnnotation(/* annotationType = */ CursorDefault::class.java)
        val limit =
            webRequest.getParameter(LIMIT)?.toIntOrNull()
                ?: annotation?.defaultLimit
                ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
        val cursorParam = webRequest.getParameter(CURSOR)

        val cursor =
            cursorParam?.let {
                val binder = (
                    binderFactory?.createBinder(webRequest, null, CURSOR)
                        ?: throw AppException(ErrorType.SERVER_ERROR)
                )

                binder.convertIfNecessary(it, getCursorType(parameter))
            }

        return Cursorable(cursor, limit)
    }

    private fun getCursorType(parameter: MethodParameter): Class<*>? = ResolvableType.forMethodParameter(parameter).getGeneric(0).resolve()
}
