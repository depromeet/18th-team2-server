package com.team2.server.common.response

import org.springframework.http.HttpStatus

data class ApiResponse<T>(
    val status: Int,
    val data: T?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(HttpStatus.OK.value(), data)

        fun success(): ApiResponse<Unit> = ApiResponse(HttpStatus.OK.value(), null)
    }
}
