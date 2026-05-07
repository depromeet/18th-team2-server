package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "홈 다가오는 파티 응답")
data class UpcomingPartyResponse(
    @Schema(description = "파티 ID", example = "1")
    val partyId: Long,
    @Schema(description = "롤링페이퍼 작성 등에 사용하는 초대 토큰. 유효한 토큰이 없으면 null", nullable = true)
    val inviteToken: String?,
    @Schema(
        description = "파티 옵션. REALTIME: 실시간 파티, PAPER_ONLY: 롤링페이퍼 전용 파티",
        allowableValues = ["REALTIME", "PAPER_ONLY"],
        example = "REALTIME",
    )
    val partyOption: PartyOption,
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String?,
    @Schema(description = "파티 시작 시각", example = "2026-05-07T20:00:00")
    val partyStartedAt: LocalDateTime,
    @Schema(description = "파티 종료 시각", example = "2026-05-14T10:00:00")
    val partyEndedAt: LocalDateTime,
    @Schema(description = "현재 회원의 주최자 여부", example = "false")
    val isHost: Boolean,
    @Schema(description = "현재 회원의 롤링페이퍼 작성 여부", example = "false")
    val rollingPaperWritten: Boolean,
    @Schema(description = "주최자 롤링페이퍼 오픈 시각. 주최자가 아니면 null", nullable = true)
    val hostRollingPaperOpenAt: LocalDateTime?,
    @Schema(description = "실시간 파티 일정. PAPER_ONLY면 null", nullable = true)
    val realtimeSchedule: UpcomingRealtimeScheduleResponse?,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "홈 다가오는 실시간 파티 일정")
data class UpcomingRealtimeScheduleResponse(
    @Schema(description = "실시간 파티 입장 가능 시작 시각", example = "2026-05-07T19:55:00")
    val enterableFrom: LocalDateTime,
    @Schema(description = "실시간 파티 시작 시각", example = "2026-05-07T20:00:00")
    val liveStartAt: LocalDateTime,
    @Schema(description = "실시간 파티 종료 시각", example = "2026-05-07T20:10:00")
    val liveEndAt: LocalDateTime,
)
