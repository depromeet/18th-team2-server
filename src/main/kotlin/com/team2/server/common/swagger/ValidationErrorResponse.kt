package com.team2.server.common.swagger

import com.team2.server.common.response.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "400",
    description = "입력값 검증 실패",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
            examples = [
                ExampleObject(
                    value = """
                        {
                          "status": 400,
                          "error": {
                            "code": "VALIDATION_ERROR",
                            "message": "nickname: 닉네임은 필수입니다"
                          }
                        }
                    """,
                ),
            ],
        ),
    ],
)
annotation class ValidationErrorResponse
