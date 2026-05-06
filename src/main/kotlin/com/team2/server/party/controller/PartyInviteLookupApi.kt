package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.common.swagger.OptionalAuth
import com.team2.server.party.dto.PartyInviteLookupResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party Invite", description = "파티 초대장 API")
interface PartyInviteLookupApi {
    @Operation(
        summary = "초대장 조회",
        description =
            "공유 링크 진입 시 초대 토큰으로 파티 요약을 조회한다. " +
                "조회 시 participant는 생성하지 않으며, 만료된 초대 토큰도 조회할 수 있다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
        ],
    )
    @OptionalAuth
    @SwaggerApiResponse(
        responseCode = "200",
        description = "초대장 조회 성공",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 초대 토큰",
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
    @InternalServerErrorResponse
    fun getPartyInvite(
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
    ): ApiResponse<PartyInviteLookupResponse>
}
