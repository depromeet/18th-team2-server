package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.AuthErrorResponses
import com.team2.server.common.web.swagger.ForbiddenResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import com.team2.server.party.dto.ActivateInviteLinkResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface PartyInviteApi {
    @Operation(
        summary = "파티 초대링크 활성화",
        description =
            "초대 토큰을 발급하거나 기존 유효 토큰을 재사용한다. " +
                "현재 파티 조회/참여 API가 보류되어 토큰 소비 경로는 새 기획 확정 후 다시 연결한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "초대링크 활성화 성공",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
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
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun activateInviteLink(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<ActivateInviteLinkResponse>
}
