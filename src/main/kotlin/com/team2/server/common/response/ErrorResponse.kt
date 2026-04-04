package com.team2.server.common.response

import org.springframework.http.HttpStatusCode

data class ErrorResponse(
    val status: Int,
    val error: ErrorDetail,
) {
    data class ErrorDetail(
        val code: String,
        val message: String,
    )

    companion object {
        fun of(
            status: HttpStatusCode,
            code: String,
            message: String,
        ): ErrorResponse = ErrorResponse(status.value(), ErrorDetail(code, message))
    }
}
