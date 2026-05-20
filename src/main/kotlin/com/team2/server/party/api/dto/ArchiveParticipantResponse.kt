package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보관함 파티 상세 - 참가자 항목")
data class ArchiveParticipantResponse(
    @Schema(description = "참가자 닉네임 (RealtimeParticipantProfile.nickname)")
    val nickname: String,
)
