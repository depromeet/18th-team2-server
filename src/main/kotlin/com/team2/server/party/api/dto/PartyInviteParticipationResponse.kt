package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "초대장 회원 참여 응답")
data class PartyInviteParticipationResponse(
    @Schema(description = "참여자 ID", example = "1")
    val participantId: Long,
)
