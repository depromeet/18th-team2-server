package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "초대링크 활성화 응답")
data class ActivateInviteLinkResponse(
    @Schema(description = "초대 토큰", example = "example-token-0000")
    val token: String,
)
