package com.team2.server.party.api.dto

import com.team2.server.party.application.dto.PartyParticipantsResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "파티 참여자 목록 응답")
data class PartyParticipantsResponse(
    @Schema(description = "현재 참여자 수", example = "4")
    val totalCount: Int,
    @Schema(description = "최대 참여자 수", example = "14")
    val maxCount: Int,
    @Schema(description = "입장 순서대로 정렬된 참여자 목록")
    val participants: List<PartyParticipantResponse>,
) {
    companion object {
        fun from(result: PartyParticipantsResult): PartyParticipantsResponse =
            PartyParticipantsResponse(
                totalCount = result.totalCount,
                maxCount = result.maxCount,
                participants = result.participants.map(PartyParticipantResponse::from),
            )
    }
}
