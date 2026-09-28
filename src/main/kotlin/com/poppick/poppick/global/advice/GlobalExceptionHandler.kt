package com.poppick.poppick.global.advice

import com.poppick.poppick.feature.planner.domain.CourseGenerationException
import com.poppick.poppick.feature.planner.domain.RouteFailedException
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiClientException
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.response.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

val logger = KotlinLogging.logger { }

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(AppException::class)
    fun handleAppException(e: AppException) =
        e
            .also { exception ->
                if (exception.errorType.logLevel == LogLevel.OFF) return@also

                val message =
                    exception.cause?.let {
                        "[AppException]: ${exception.errorType} | ${exception.message} | " +
                            "Caused by: ${it.javaClass.simpleName} - ${it.message}"
                    } ?: "[AppException]: ${exception.errorType} | ${exception.message}"

                logger.at(exception.errorType.logLevel.toKLogLevel()) {
                    this.message = message
                    this.cause = exception
                }
            }.toErrorResponse()

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException) =
        e
            .also {
                logger.warn { "[Authentication] failed: ${it.javaClass.simpleName} - ${it.message}" }
            }.toAppException()
            .toErrorResponse()

    /** Bean Validation 실패. 필드별 사유를 data 로 내린다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException) =
        e.bindingResult.fieldErrors
            .map { FieldErrorData(it.field, it.defaultMessage) }
            .also { logger.warn { "[Validation] failed: $it" } }
            .let { AppException(ErrorType.INVALID_REQUEST, errorData = it).toErrorResponse() }

    /** 본문 JSON 형식 오류(잘못된 enum · 날짜 · 필드 타입 등). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException) =
        e
            .also { logger.warn { "[Validation] unreadable body: ${it.mostSpecificCause.message}" } }
            .let { AppException(ErrorType.INVALID_REQUEST).toErrorResponse() }

    /** LLM 코스 생성 실패(응답 검증 실패 · OpenAI 호출 실패). */
    @ExceptionHandler(CourseGenerationException::class, OpenAiClientException::class)
    fun handleGenerationFailed(e: RuntimeException) =
        e
            .also { logger.warn(it) { "[Planner] 코스 생성 실패: ${it.javaClass.simpleName} - ${it.message}" } }
            .let { AppException(ErrorType.GENERATION_FAILED, cause = it).toErrorResponse() }

    /** 도보 경로 실패. */
    @ExceptionHandler(RouteFailedException::class)
    fun handleRouteFailed(e: RouteFailedException) =
        e
            .also { logger.warn(it) { "[Planner] 경로 실패: ${it.message}" } }
            .let { AppException(ErrorType.ROUTE_FAILED, cause = it).toErrorResponse() }

    data class FieldErrorData(
        val field: String,
        val message: String?,
    )

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception) =
        e
            .also {
                logger.error(it) { "[Unexpected Exception]: ${it.message}" }
            }.toErrorResponse()

    private fun LogLevel.toKLogLevel(): Level =
        when (this) {
            LogLevel.TRACE -> Level.TRACE
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.INFO -> Level.INFO
            LogLevel.WARN -> Level.WARN
            LogLevel.ERROR, LogLevel.FATAL -> Level.ERROR
            LogLevel.OFF -> Level.OFF
        }

    private fun AuthenticationException.toAppException() =
        when (this) {
            is AuthenticationCredentialsNotFoundException,
            is InsufficientAuthenticationException,
            -> AppException(ErrorType.REQUIRED_AUTH, cause = this)

            else -> AppException(ErrorType.FAILED_AUTH, cause = this)
        }

    private fun AppException.toErrorResponse() =
        ResponseEntity(
            ApiResponse.error(errorType, errorData),
            errorType.status,
        )

    private fun Exception.toErrorResponse() =
        ResponseEntity(
            ApiResponse.error(ErrorType.SERVER_ERROR, this.message),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
}
