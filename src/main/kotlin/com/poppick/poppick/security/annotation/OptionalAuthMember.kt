package com.poppick.poppick.security.annotation

import org.springframework.security.core.annotation.AuthenticationPrincipal

/**
 * permitAll 경로용. 로그인 회원이면 Member, 익명(principal "anonymousUser") · 인증 없음이면 null.
 * 파라미터 타입은 Member? 로 받는다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@AuthenticationPrincipal(expression = "#this instanceof T(com.poppick.poppick.security.domain.AuthMember) ? member : null")
annotation class OptionalAuthMember
