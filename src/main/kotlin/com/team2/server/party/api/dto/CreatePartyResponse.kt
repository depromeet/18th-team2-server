package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "파티 생성 응답")
data class CreatePartyResponse(
    @Schema(description = "생성된 파티 ID", example = "1")
    val partyId: Long,
)
