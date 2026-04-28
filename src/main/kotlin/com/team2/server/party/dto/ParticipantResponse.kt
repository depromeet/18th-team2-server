package com.team2.server.party.dto

import com.team2.server.party.entity.Participant
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "참여자 정보")
data class ParticipantResponse(
    @Schema(description = "참여자 ID", example = "10")
    val participantId: Long,
    @Schema(description = "참여자 닉네임", example = "홍길동")
    val nickname: String?,
    @Schema(description = "캐릭터 이미지 URL", example = "/images/characters/character1.jpg")
    val characterImageUrl: String?,
) {
    companion object {
        fun from(
            participant: Participant,
            characterImageUrl: String?,
        ): ParticipantResponse =
            ParticipantResponse(
                participantId = participant.id,
                nickname = participant.nickname,
                characterImageUrl = characterImageUrl,
            )

        fun joined(
            participant: Participant,
            characterImageUrl: String?,
        ): ParticipantResponse = from(participant, characterImageUrl)
    }
}
