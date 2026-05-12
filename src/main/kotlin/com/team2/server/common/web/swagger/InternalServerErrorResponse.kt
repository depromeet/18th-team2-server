package com.team2.server.common.web.swagger

import com.team2.server.common.web.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "500",
    description = "서버 내부 오류",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
            examples = [
                ExampleObject(
                    value = """
                        {
                          "status": 500,
                          "error": {
                            "code": "INTERNAL_SERVER_ERROR",
                            "message": "서버 내부 오류가 발생했습니다"
                          }
                        }
                    """,
                ),
            ],
        ),
    ],
)
annotation class InternalServerErrorResponse
