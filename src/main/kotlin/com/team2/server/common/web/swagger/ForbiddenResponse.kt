package com.team2.server.common.web.swagger

import com.team2.server.common.web.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponse(
    responseCode = "403",
    description = "권한 없음",
    content = [
        Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
            examples = [
                ExampleObject(
                    value = """
                        {
                          "status": 403,
                          "error": {
                            "code": "PARTY_FORBIDDEN",
                            "message": "파티에 대한 권한이 없습니다"
                          }
                        }
                    """,
                ),
            ],
        ),
    ],
)
annotation class ForbiddenResponse
