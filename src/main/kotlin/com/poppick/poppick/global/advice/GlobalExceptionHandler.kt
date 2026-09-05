package com.poppick.poppick.global.advice

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.boot.logging.LogLevel
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AppException::class)
    fun handleAppException(e: AppException) = e.also { exception ->
        if (exception.errorType.logLevel == LogLevel.OFF) return@also

        val message = exception.cause?.let {
            "[AppException]: ${exception.errorType} | ${exception.message} | " +
                "Caused by: ${it.javaClass.simpleName} - ${it.message}"
        } ?: "[AppException]: ${exception.errorType} | ${exception.message}"

        logger.atLevel(exception.errorType.logLevel.toSlf4jLevel())
            .setCause(exception)
            .log(message)
    }.toErrorResponse()

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(e: AuthenticationException) = e.also {
        logger.warn("[Authentication] failed: ${it.javaClass.simpleName} - ${it.message}")
    }.toAppException().toErrorResponse()

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception) = e.also {
        logger.error("[Unexpected Exception]: ${it.message}", it)
    }.toErrorResponse()

    private fun LogLevel.toSlf4jLevel(): Level = when (this) {
        LogLevel.TRACE -> Level.TRACE
        LogLevel.DEBUG -> Level.DEBUG
        LogLevel.INFO -> Level.INFO
        LogLevel.WARN -> Level.WARN
        LogLevel.ERROR, LogLevel.FATAL -> Level.ERROR
        LogLevel.OFF -> Level.ERROR
    }

    private fun AuthenticationException.toAppException() = when (this) {
        is AuthenticationCredentialsNotFoundException,
        is InsufficientAuthenticationException -> AppException(ErrorType.REQUIRED_AUTH, cause = this)
        else -> AppException(ErrorType.FAILED_AUTH, cause = this)
    }

    private fun AppException.toErrorResponse() = ResponseEntity(
        ErrorResponse(
            code = errorType.errorCode.name,
            message = message ?: errorType.message,
            data = errorData
        ),
        errorType.status
    )

    private fun Exception.toErrorResponse() = ResponseEntity(
        ErrorResponse(
            code = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred"
        ),
        HttpStatus.INTERNAL_SERVER_ERROR
    )

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val data: Any? = null,
)
