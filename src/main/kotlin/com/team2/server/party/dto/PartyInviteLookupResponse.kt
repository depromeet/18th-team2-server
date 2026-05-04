package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "초대장 조회 응답")
data class PartyInviteLookupResponse(
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String?,
    @Schema(
        description = "파티 옵션. REALTIME: 실시간 파티, PAPER_ONLY: 롤링페이퍼 전용 파티",
        allowableValues = ["REALTIME", "PAPER_ONLY"],
        example = "REALTIME",
    )
    val partyOption: PartyOption,
    @Schema(description = "파티 자체 종료 여부", example = "false")
    val partyEnded: Boolean,
    @Schema(description = "현재 조회자의 롤링페이퍼 작성 여부", example = "false")
    val rollingPaperWritten: Boolean,
    @Schema(description = "파티 시작일", example = "2026-05-04")
    val partyStartDate: LocalDate,
    @Schema(description = "파티 종료일", example = "2026-05-11")
    val partyEndDate: LocalDate,
    @Schema(
        description =
            "실시간 파티 입장 상태. " +
                "NOT_ENTERABLE: 입장 가능 시간 전, ENTERABLE: 입장 가능, ENDED: 실시간 진행 종료. " +
                "PAPER_ONLY면 null",
        allowableValues = ["NOT_ENTERABLE", "ENTERABLE", "ENDED"],
        example = "ENTERABLE",
    )
    val realtimeStatus: RealtimeStatus?,
    @Schema(description = "실시간 파티 시작 시각. PAPER_ONLY면 null", example = "2026-05-04T20:00:00")
    val liveStartAt: LocalDateTime?,
    @Schema(description = "실시간 파티 진행 시간. PAPER_ONLY면 null", example = "10")
    val liveDurationMinutes: Long?,
)

@Schema(
    description =
        "초대장 조회 응답용 실시간 파티 입장 상태. " +
            "NOT_ENTERABLE: 입장 가능 시간 전, ENTERABLE: 입장 가능, ENDED: 실시간 진행 종료",
)
enum class RealtimeStatus {
    @Schema(description = "입장 가능 시간 전")
    NOT_ENTERABLE,

    @Schema(description = "입장 가능")
    ENTERABLE,

    @Schema(description = "실시간 진행 종료")
    ENDED,
}
