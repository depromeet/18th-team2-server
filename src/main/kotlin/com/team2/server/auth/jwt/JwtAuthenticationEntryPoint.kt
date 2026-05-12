package com.team2.server.auth.jwt

import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

const val AUTH_ERROR_REQUEST_ATTRIBUTE = "authErrorCode"

@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val errorCode =
            (request.getAttribute(AUTH_ERROR_REQUEST_ATTRIBUTE) as? ErrorCode)
                ?: ErrorCode.AUTH_UNAUTHORIZED
        val body = ErrorResponse.of(errorCode.httpStatus, errorCode.name, errorCode.message)

        response.status = errorCode.httpStatus.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.outputStream, body)
    }
}
