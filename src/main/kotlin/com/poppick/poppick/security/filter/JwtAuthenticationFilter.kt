package com.poppick.poppick.security.filter

import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.security.domain.AuthMember
import com.poppick.poppick.security.enums.TokenType
import com.poppick.poppick.security.jwt.JwtValidator
import com.poppick.poppick.security.session.AccessTokenBlacklist
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val memberFinder: MemberFinder,
    private val jwtValidator: JwtValidator,
    private val accessTokenBlacklist: AccessTokenBlacklist,
    private val antPathMatcher: AntPathMatcher,
) : OncePerRequestFilter() {
    companion object {
        private val PUBLIC_ENDPOINTS = listOf(
            "/actuator",
            "/health",
            "/api/v1/auth/**",
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return PUBLIC_ENDPOINTS.any { antPathMatcher.match(it, request.requestURI) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        getTokenFromHeader(request)?.let { authenticate(it) }

        doFilter(request, response, filterChain)
    }

    private fun getTokenFromHeader(request: HttpServletRequest): String? = request.getHeader(HttpHeaders.AUTHORIZATION)

    private fun authenticate(token: String) {
        val tokenBody = jwtValidator.getBearerTokenBody(token)

        jwtValidator.validateTokenType(tokenBody, TokenType.ACCESS)
        val subject = jwtValidator.getSubject(tokenBody)

        if (accessTokenBlacklist.contains(jwtValidator.getJti(tokenBody))) {
            throw AppException(ErrorType.FAILED_AUTH)
        }

        memberFinder.find(subject).run {
            val authMember = AuthMember(this, emptyMap(), authorities)

            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(authMember, null, authorities)
        }
    }
}