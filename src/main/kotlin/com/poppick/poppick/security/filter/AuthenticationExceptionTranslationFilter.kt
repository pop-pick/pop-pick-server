package com.poppick.poppick.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Component
class AuthenticationExceptionTranslationFilter(
    @Qualifier("handlerExceptionResolver")
    private val resolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        runCatching {
            filterChain.doFilter(request, response)
        }.onFailure { throwable ->
            if (response.isCommitted) {
                throw throwable
            }

            when (throwable) {
                is Exception -> resolver.resolveException(request, response, null, throwable)
                else -> throw throwable
            }
        }
    }
}