package com.team2.server.common.web

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(ObjectMapper())

    @Test
    fun `business exception은 error code 상태와 메시지로 응답한다`() {
        val response = MockHttpServletResponse()

        handler.handleBusinessException(BusinessException(ErrorCode.BURST_GAME_NOT_READY), response)

        assertError(response, HttpStatus.BAD_REQUEST, "BURST_GAME_NOT_READY", ErrorCode.BURST_GAME_NOT_READY.message)
    }

    @Test
    fun `request body validation 실패는 field error 메시지를 합쳐 응답한다`() {
        val bindingResult = BeanPropertyBindingResult(Target(), "target")
        bindingResult.addError(FieldError("target", "name", "must not be blank"))
        bindingResult.addError(FieldError("target", "age", "must be greater than 0"))
        val response = MockHttpServletResponse()

        handler.handleValidationException(MethodArgumentNotValidException(mock(), bindingResult), response)

        assertError(
            response,
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "name: must not be blank, age: must be greater than 0",
        )
    }

    @Test
    fun `method validation 실패는 공통 validation error로 응답한다`() {
        val exception: HandlerMethodValidationException = mock()
        whenever(exception.statusCode).thenReturn(HttpStatus.BAD_REQUEST)
        val response = MockHttpServletResponse()

        handler.handleMethodValidationException(exception, response)

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "잘못된 요청 값입니다.")
    }

    @Test
    fun `constraint violation 메시지가 없으면 기본 validation 메시지로 응답한다`() {
        val response = MockHttpServletResponse()

        handler.handleConstraintViolationException(ConstraintViolationException(emptySet()), response)

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "잘못된 요청 값입니다.")
    }

    @Test
    fun `constraint violation 메시지를 합쳐 응답한다`() {
        val first: ConstraintViolation<Target> = mock()
        whenever(first.message).thenReturn("first invalid")
        val response = MockHttpServletResponse()

        handler.handleConstraintViolationException(ConstraintViolationException(setOf(first)), response)

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "first invalid")
    }

    @Test
    fun `type mismatch는 입력값을 포함한 invalid input으로 응답한다`() {
        val exception =
            MethodArgumentTypeMismatchException(
                "abc",
                Long::class.java,
                "partyId",
                mock(),
                IllegalArgumentException("invalid"),
            )
        val response = MockHttpServletResponse()

        handler.handleTypeMismatchException(exception, response)

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 요청 값입니다: abc")
    }

    @Test
    fun `알 수 없는 exception은 internal server error로 응답한다`() {
        val response = MockHttpServletResponse()

        handler.handleException(IllegalStateException("unexpected"), response)

        assertError(
            response,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            ErrorCode.INTERNAL_SERVER_ERROR.message,
        )
    }

    private fun assertError(
        response: MockHttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        assertEquals(status.value(), response.status)
        assertContains(response.contentAsString, "\"code\":\"$code\"")
        assertContains(response.contentAsString, "\"message\":\"$message\"")
    }

    private data class Target(
        val name: String = "",
        val age: Int = 0,
    )
}
