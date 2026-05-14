package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "실시간 파티 입장 프로필 응답")
data class ParticipantRealtimeProfileResponse(
    @Schema(description = "회원 participant ID", example = "1")
    val participantId: Long,
    @Schema(description = "주최자 여부", example = "false")
    val isHost: Boolean,
    @Schema(description = "현재 저장된 닉네임. 미설정이면 null", example = "안녕용가리", nullable = true)
    val nickname: String?,
    @Schema(description = "닉네임 수정 가능 여부. 주최자는 false", example = "true")
    val nicknameEditable: Boolean,
    @Schema(description = "선택된 캐릭터. 미선택이면 null", nullable = true)
    val character: CharacterResponse?,
) {
    companion object {
        fun from(result: ParticipantRealtimeProfileResult): ParticipantRealtimeProfileResponse =
            ParticipantRealtimeProfileResponse(
                participantId = result.participantId,
                isHost = result.isHost,
                nickname = result.nickname,
                nicknameEditable = result.nicknameEditable,
                character = result.character?.let { CharacterResponse.from(it) },
            )
    }
}
