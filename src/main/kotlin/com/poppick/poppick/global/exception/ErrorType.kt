package com.poppick.poppick.global.exception

import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus

enum class ErrorType(
    val status: HttpStatus,
    val errorCode: ErrorCode,
    val message: String,
    val logLevel: LogLevel,
) {
    NOT_FOUND_DATA(HttpStatus.NOT_FOUND, ErrorCode.E404, "해당 데이터를 찾을 수 없습니다.", LogLevel.WARN),
    SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E500, "알 수 없는 오류가 발생했습니다.", LogLevel.ERROR),
    INVALID_PAGING_SIZE(HttpStatus.BAD_REQUEST, ErrorCode.E400, "잘못된 페이징 크기입니다.", LogLevel.WARN),
    INVALID_PAGING_PARAMETER(HttpStatus.BAD_REQUEST, ErrorCode.E400, "페이징 요소가 누락되었습니다.", LogLevel.WARN),
    ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, ErrorCode.E400, "이미 데이터가 존재합니다.", LogLevel.WARN),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, ErrorCode.E400, "요청 값이 올바르지 않습니다.", LogLevel.WARN),

    // Security
    REQUIRED_AUTH(HttpStatus.UNAUTHORIZED, ErrorCode.E1000, "인증이 필요합니다.", LogLevel.WARN),
    FAILED_AUTH(HttpStatus.UNAUTHORIZED, ErrorCode.E1001, "인증에 실패했습니다.", LogLevel.WARN),
    MALFORMED_JWT(HttpStatus.BAD_REQUEST, ErrorCode.E1002, "JWT가 손상되었습니다.", LogLevel.WARN),
    UNSUPPORTED_JWT(HttpStatus.BAD_REQUEST, ErrorCode.E1003, "지원하지 않는 JWT 형식입니다.", LogLevel.WARN),
    EXPIRED_JWT(HttpStatus.UNAUTHORIZED, ErrorCode.E1004, "JWT 기한이 만료되었습니다.", LogLevel.WARN),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, ErrorCode.E1005, "JWT Signature 검증에 실패했습니다.", LogLevel.WARN),
    INVALID_JWT(HttpStatus.BAD_REQUEST, ErrorCode.E1006, "JWT가 유효하지 않습니다.", LogLevel.WARN),
    INVALID_TOKEN_METHOD(HttpStatus.BAD_REQUEST, ErrorCode.E1007, "토큰 방식이 올바르지 않습니다.", LogLevel.WARN),
    INVALID_TOKEN_TYPE(HttpStatus.BAD_REQUEST, ErrorCode.E1008, "토큰 타입이 올바르지 않습니다.", LogLevel.WARN),
    INVALID_OAUTH_USER(HttpStatus.BAD_REQUEST, ErrorCode.E1009, "존재하지 않는 OAuth 유저입니다.", LogLevel.WARN),
    UNSUPPORTED_PROVIDER(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E1010, "지원하지 않는 공급자 입니다.", LogLevel.ERROR),
    INVALID_REFRESH_SESSION(HttpStatus.UNAUTHORIZED, ErrorCode.E1011, "유효하지 않거나 재사용된 refresh 토큰입니다.", LogLevel.WARN),
    FAILED_REVOKE_ACCESS(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E1012, "Access Blacklist 삭제를 실패했습니다.", LogLevel.ERROR),
    FAILED_REVOKE_REFRESH(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.E1013, "Refresh sid 삭제를 실패했습니다.", LogLevel.ERROR),

    // Member
    INVALID_MEMBER_KEY(HttpStatus.BAD_REQUEST, ErrorCode.E2000, "멤버 key가 유효하지 않습니다.", LogLevel.WARN),
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, ErrorCode.E1002, "이메일이 유효하지 않습니다.", LogLevel.WARN),

    // Planner
    PLANNER_NOT_FOUND(HttpStatus.NOT_FOUND, ErrorCode.E3000, "플래너를 찾을 수 없어요.", LogLevel.WARN),
    PLANNER_FORBIDDEN(HttpStatus.FORBIDDEN, ErrorCode.E3001, "이 플래너에 접근할 수 없어요.", LogLevel.WARN),
    INVALID_VISIT_DATE(HttpStatus.BAD_REQUEST, ErrorCode.E3002, "방문일은 오늘부터 30일 이내로 골라주세요.", LogLevel.WARN),
    INVALID_START_TIME(HttpStatus.BAD_REQUEST, ErrorCode.E3003, "시작 시각은 08:00~20:00 사이, 오늘이면 지금부터 30분 이후로 골라주세요.", LogLevel.WARN),
    INSUFFICIENT_POPUPS(
        HttpStatus.UNPROCESSABLE_CONTENT,
        ErrorCode.E3004,
        "선택한 지역에 추천할 팝업이 충분하지 않아요. 다른 지역이나 날짜를 골라주세요.",
        LogLevel.INFO,
    ),
    INVALID_PLANNER_STATUS(HttpStatus.CONFLICT, ErrorCode.E3005, "지금 상태에서는 할 수 없는 요청이에요.", LogLevel.WARN),
    GENERATION_FAILED(HttpStatus.BAD_GATEWAY, ErrorCode.E3006, "코스를 만들지 못했어요. 잠시 후 다시 시도해주세요.", LogLevel.WARN),
    ROUTE_FAILED(HttpStatus.BAD_GATEWAY, ErrorCode.E3007, "이동 경로를 찾지 못했어요. 잠시 후 다시 시도해주세요.", LogLevel.WARN),
}
