package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.application.dto.ArchiveRole
import com.team2.server.party.domain.entity.Participant
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneOffset

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 항목")
data class ArchiveListItemResponse(
    @Schema(description = "항목 ID (participant.id)", example = "1024")
    val id: String,
    @Schema(description = "파티 ID (상세 조회 API 호출용)", example = "42")
    val partyId: Long,
    @Schema(description = "항목 타입", allowableValues = ["PARTY", "PAPER"], example = "PARTY")
    val type: ArchiveItemType,
    @Schema(description = "조회자 역할", allowableValues = ["HOST", "PARTICIPANT"], example = "HOST")
    val role: ArchiveRole,
    @Schema(description = "파티 주인공 닉네임. 없으면 null", example = "김루카", nullable = true)
    val celebrantName: String?,
    @Schema(description = "파티 종료 시각 (KST 오프셋)", example = "2026-05-12T22:10:00+09:00")
    val date: OffsetDateTime,
) {
    companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)

        fun from(
            participant: Participant,
            userId: Long,
        ): ArchiveListItemResponse {
            val party = participant.party
            return ArchiveListItemResponse(
                id = participant.id.toString(),
                partyId = party.id,
                type = ArchiveItemType.from(party.partyOption),
                role = if (party.ownerId == userId) ArchiveRole.HOST else ArchiveRole.PARTICIPANT,
                celebrantName = party.celebrantNickname,
                date = party.endedAt().atOffset(KST),
            )
        }
    }
}
