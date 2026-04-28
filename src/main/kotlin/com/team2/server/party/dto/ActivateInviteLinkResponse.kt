package com.team2.server.party.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "초대링크 활성화 응답")
data class ActivateInviteLinkResponse(
    @Schema(description = "초대 토큰", example = "a1b2c3d4e5f67890")
    val token: String,
)
