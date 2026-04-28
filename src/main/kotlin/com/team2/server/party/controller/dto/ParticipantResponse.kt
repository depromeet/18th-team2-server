package com.team2.server.party.controller.dto

import com.team2.server.party.entity.Participant
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "참여자 정보")
data class ParticipantResponse(
    @Schema(description = "참여자 ID", example = "10")
    val participantId: Long,
    @Schema(description = "참여자 닉네임", example = "홍길동")
    val nickname: String?,
    @Schema(description = "캐릭터 ID", example = "1")
    val characterId: Long,
) {
    companion object {
        fun from(participant: Participant): ParticipantResponse =
            ParticipantResponse(
                participantId = participant.id,
                nickname = participant.nickname,
                characterId = participant.character.id,
            )
    }
}
