package com.team2.server.party.dto

import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "파티 정보 조회 응답")
data class PartyInfoResponse(
    @Schema(description = "파티 이름", example = "생일파티")
    val name: String?,
    @Schema(description = "주인공 닉네임", example = "홍길동")
    val celebrantNickname: String?,
    @Schema(description = "파티 목적")
    val purpose: PartyPurpose?,
    @Schema(description = "파티 옵션")
    val option: PartyOption?,
    @Schema(description = "파티 시작일")
    val startedAt: LocalDateTime?,
    @Schema(description = "파티 종료일")
    val endedAt: LocalDateTime?,
    @Schema(description = "파티 종료 여부")
    val ended: Boolean,
    @Schema(description = "나의 참여 정보 (회원이 이미 참여한 경우)")
    val myParticipant: ParticipantResponse?,
) {
    companion object {
        fun from(
            party: Party,
            myParticipant: ParticipantResponse?,
        ): PartyInfoResponse {
            val now = LocalDateTime.now()
            return PartyInfoResponse(
                name = party.name,
                celebrantNickname = party.celebrantNickname,
                purpose = party.purpose,
                option = party.option,
                startedAt = party.startedAt,
                endedAt = party.endedAt,
                ended = party.endedAt?.let { !it.isAfter(now) } ?: false,
                myParticipant = myParticipant,
            )
        }
    }
}
