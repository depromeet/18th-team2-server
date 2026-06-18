package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimePartyStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "초대장 조회 응답")
data class PartyInviteLookupResponse(
    @Schema(description = "파티 ID", example = "1")
    val partyId: Long,
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String?,
    @Schema(description = "현재 조회자가 파티 주최자인지 여부", example = "false")
    val isHost: Boolean,
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
        description = "실시간 파티 일정 기준 시각. PAPER_ONLY면 null",
        nullable = true,
    )
    val realtimeSchedule: RealtimeSchedule?,
    @Schema(
        description = "현재 실시간 파티 상태. PAPER_ONLY면 null",
        allowableValues = [
            "ROLLING_PAPER_OPEN",
            "LIVE_OPEN",
            "LIVE_ENDING",
            "LIVE_CLOSED",
            "ROLLING_PAPER_CLOSED",
        ],
        nullable = true,
        example = "LIVE_ENDING",
    )
    val realtimeStatus: RealtimePartyStatus?,
    @Schema(description = "현재 실시간 파티 신규/재입장 가능 여부", example = "false")
    val realtimeEnterable: Boolean,
)

@Schema(description = "실시간 파티 일정 기준 시각")
data class RealtimeSchedule(
    @Schema(description = "실시간 파티 시작 시각", example = "2026-05-04T20:00:00")
    val liveStartAt: LocalDateTime,
    @Schema(description = "파티 입장 가능 시작 시각", example = "2026-05-04T19:55:00")
    val enterableFrom: LocalDateTime,
    @Schema(description = "실시간 파티 종료 시각", example = "2026-05-04T20:10:00")
    val liveEndAt: LocalDateTime,
    @Schema(description = "실시간 파티 진행 시간", example = "10")
    val liveDurationMinutes: Long,
)
