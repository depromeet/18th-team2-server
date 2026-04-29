package com.team2.server.common.response

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus

@Schema(description = "공통 성공 응답")
data class ApiResponse<T>(
    @Schema(description = "HTTP 상태 코드", example = "200")
    val status: Int,
    @Schema(description = "응답 데이터")
    val data: T?,
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(HttpStatus.OK.value(), data)

        fun <T> success(status: HttpStatus, data: T): ApiResponse<T> = ApiResponse(status.value(), data)

        fun success(): ApiResponse<Unit> = ApiResponse(HttpStatus.OK.value(), null)
    }
}
