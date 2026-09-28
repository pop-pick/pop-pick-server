package com.poppick.poppick.feature.planner.domain

/** 도보 경로를 만들지 못했다(카카오 status != OK · HTTP 실패). ErrorType(ROUTE_FAILED) 매핑은 호출 측에서 한다. */
class RouteFailedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
