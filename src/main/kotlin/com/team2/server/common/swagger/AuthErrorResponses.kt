package com.team2.server.common.swagger

import com.team2.server.common.response.ErrorResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
    ApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "인증 필요",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_UNAUTHORIZED",
                                "message": "인증이 필요합니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "만료된 토큰",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_EXPIRED_TOKEN",
                                "message": "만료된 토큰입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "유효하지 않은 토큰",
                        value = """
                            {
                              "status": 401,
                              "error": {
                                "code": "AUTH_INVALID_TOKEN",
                                "message": "유효하지 않은 토큰입니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    ),
)
annotation class AuthErrorResponses
