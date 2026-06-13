package com.team2.server.party.api.dto

import com.team2.server.party.application.dto.PartyParticipantResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "파티 참여자 항목")
data class PartyParticipantResponse(
    @Schema(description = "참여자 ID", example = "17")
    val participantId: Long,
    @Schema(description = "입장 순서 (1부터)", example = "1")
    val joinOrder: Int,
    @Schema(description = "닉네임", example = "주최자닉")
    val nickname: String,
    @Schema(description = "캐릭터 ID", example = "3", nullable = true)
    val characterId: Long?,
    @Schema(description = "캐릭터 메인 이미지 URL", nullable = true)
    val characterImageUrl: String?,
    @Schema(description = "캐릭터 썸네일 이미지 URL", nullable = true)
    val thumbnailImageUrl: String?,
    @Schema(description = "파티 주최자 여부", example = "true")
    val isOwner: Boolean,
    @Schema(description = "파티 주인공 여부", example = "true")
    val isCelebrant: Boolean,
    @Schema(description = "조회자 본인 여부", example = "false")
    val isMe: Boolean,
) {
    companion object {
        fun from(result: PartyParticipantResult): PartyParticipantResponse =
            PartyParticipantResponse(
                participantId = result.participantId,
                joinOrder = result.joinOrder,
                nickname = result.nickname,
                characterId = result.characterId,
                characterImageUrl = result.characterImageUrl,
                thumbnailImageUrl = result.thumbnailImageUrl,
                isOwner = result.isOwner,
                isCelebrant = result.isCelebrant,
                isMe = result.isMe,
            )
    }
}
