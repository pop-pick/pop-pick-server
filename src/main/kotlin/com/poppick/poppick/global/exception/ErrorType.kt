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
}