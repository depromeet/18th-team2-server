package com.team2.server.common.web

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.ObjectMapper

@RestControllerAdvice
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        e: BusinessException,
        response: HttpServletResponse,
    ) {
        val errorCode = e.errorCode
        writeErrorResponse(
            response,
            errorCode.httpStatus.value(),
            ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        e: MethodArgumentNotValidException,
        response: HttpServletResponse,
    ) {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        writeErrorResponse(
            response,
            e.statusCode.value(),
            ErrorResponse.of(e.statusCode, "VALIDATION_ERROR", message),
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidationException(
        e: HandlerMethodValidationException,
        response: HttpServletResponse,
    ) {
        writeErrorResponse(
            response,
            e.statusCode.value(),
            ErrorResponse.of(e.statusCode, "VALIDATION_ERROR", "잘못된 요청 값입니다."),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        e: ConstraintViolationException,
        response: HttpServletResponse,
    ) {
        val message =
            e.constraintViolations
                .joinToString(", ") { it.message }
                .ifBlank { "잘못된 요청 값입니다." }
        writeErrorResponse(
            response,
            HttpStatus.BAD_REQUEST.value(),
            ErrorResponse.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        e: MethodArgumentTypeMismatchException,
        response: HttpServletResponse,
    ) {
        writeErrorResponse(
            response,
            HttpStatus.BAD_REQUEST.value(),
            ErrorResponse.of(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 요청 값입니다: ${e.value}"),
        )
    }

    @ExceptionHandler(AsyncRequestNotUsableException::class)
    fun handleAsyncRequestNotUsableException(e: AsyncRequestNotUsableException) {
        log.debug("Client disconnected during async request", e)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        e: Exception,
        response: HttpServletResponse,
    ) {
        log.error("Unexpected error", e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        writeErrorResponse(
            response,
            errorCode.httpStatus.value(),
            ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message),
        )
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        status: Int,
        body: ErrorResponse,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, body)
    }
}
