package com.team2.server.common.response

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatusCode

@Schema(description = "공통 에러 응답")
data class ErrorResponse(
    @Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @Schema(description = "에러 상세")
    val error: ErrorDetail,
) {
    @Schema(description = "에러 상세 정보")
    data class ErrorDetail(
        @Schema(description = "에러 코드", example = "PARTY_NOT_FOUND")
        val code: String,
        @Schema(description = "에러 메시지", example = "파티를 찾을 수 없습니다")
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
