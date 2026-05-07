package com.team2.server.rollingpaper.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperDetailResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Rolling Paper", description = "롤링페이퍼 API")
interface RollingPaperOwnerApi {
    @Operation(
        summary = "주최자용 롤링페이퍼 목록 조회",
        description = "인증된 파티 소유자가 롤링페이퍼 목록을 조회한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "주최자용 롤링페이퍼 목록 조회 성공",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "403",
        description = "파티 권한 없음 또는 아직 열람 불가",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 권한 없음",
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
                    ExampleObject(
                        name = "아직 열람 불가",
                        value = """
                            {
                              "status": 403,
                              "error": {
                                "code": "ROLLING_PAPER_NOT_VIEWABLE",
                                "message": "아직 롤링페이퍼를 확인할 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getOwnerRollingPapers(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
        @Parameter(description = "페이지 번호. 1보다 작으면 1로 보정합니다.", example = "1") page: Int,
    ): ApiResponse<OwnerRollingPaperListResponse>

    @Operation(
        summary = "주최자용 롤링페이퍼 상세 조회",
        description = "인증된 파티 소유자가 롤링페이퍼 상세 내용을 조회한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "주최자용 롤링페이퍼 상세 조회 성공",
    )
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "403",
        description = "파티 권한 없음 또는 아직 열람 불가",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 권한 없음",
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
                    ExampleObject(
                        name = "아직 열람 불가",
                        value = """
                            {
                              "status": 403,
                              "error": {
                                "code": "ROLLING_PAPER_NOT_VIEWABLE",
                                "message": "아직 롤링페이퍼를 확인할 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 또는 롤링페이퍼 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "롤링페이퍼 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "ROLLING_PAPER_NOT_FOUND",
                                "message": "롤링페이퍼를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getOwnerRollingPaperDetail(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
        @Parameter(description = "롤링페이퍼 ID", example = "10") rollingPaperId: Long,
    ): ApiResponse<OwnerRollingPaperDetailResponse>
}
