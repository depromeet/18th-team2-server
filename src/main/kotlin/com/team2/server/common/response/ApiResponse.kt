package com.team2.server.common.response

data class ApiResponse<T>(
    val status: Int,
    val data: T?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(200, data)

        fun success(): ApiResponse<Unit> = ApiResponse(200, null)
    }
}
