package com.team2.server.common.web

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `business exception은 error code 상태와 메시지로 응답한다`() {
        val response = handler.handleBusinessException(BusinessException(ErrorCode.BURST_GAME_NOT_READY))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("BURST_GAME_NOT_READY", response.body?.error?.code)
        assertEquals(ErrorCode.BURST_GAME_NOT_READY.message, response.body?.error?.message)
    }

    @Test
    fun `request body validation 실패는 field error 메시지를 합쳐 응답한다`() {
        val bindingResult = BeanPropertyBindingResult(Target(), "target")
        bindingResult.addError(FieldError("target", "name", "must not be blank"))
        bindingResult.addError(FieldError("target", "age", "must be greater than 0"))

        val response = handler.handleValidationException(MethodArgumentNotValidException(mock(), bindingResult))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("name: must not be blank, age: must be greater than 0", response.body?.error?.message)
    }

    @Test
    fun `method validation 실패는 공통 validation error로 응답한다`() {
        val exception: HandlerMethodValidationException = mock()
        whenever(exception.statusCode).thenReturn(HttpStatus.BAD_REQUEST)

        val response = handler.handleMethodValidationException(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("잘못된 요청 값입니다.", response.body?.error?.message)
    }

    @Test
    fun `constraint violation 메시지가 없으면 기본 validation 메시지로 응답한다`() {
        val response = handler.handleConstraintViolationException(ConstraintViolationException(emptySet()))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("잘못된 요청 값입니다.", response.body?.error?.message)
    }

    @Test
    fun `constraint violation 메시지를 합쳐 응답한다`() {
        val first: ConstraintViolation<Target> = mock()
        whenever(first.message).thenReturn("first invalid")

        val response = handler.handleConstraintViolationException(ConstraintViolationException(setOf(first)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.error?.code)
        assertEquals("first invalid", response.body?.error?.message)
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

        val response = handler.handleTypeMismatchException(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_INPUT", response.body?.error?.code)
        assertEquals("잘못된 요청 값입니다: abc", response.body?.error?.message)
    }

    @Test
    fun `알 수 없는 exception은 internal server error로 응답한다`() {
        val response = handler.handleException(IllegalStateException("unexpected"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.error?.code)
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.error?.message)
    }

    private data class Target(
        val name: String = "",
        val age: Int = 0,
    )
}
