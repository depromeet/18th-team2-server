package com.team2.server.common.exception

import com.team2.server.common.response.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        val errorCode = e.errorCode
        val response = ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        val response = ErrorResponse.of(e.statusCode, "VALIDATION_ERROR", message)
        return ResponseEntity.status(e.statusCode).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        val response = ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message)
        return ResponseEntity.status(errorCode.httpStatus).body(response)
    }
}
