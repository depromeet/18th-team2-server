package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimePartyStatus
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
    @Schema(
        description =
            "주최자에게 롤링페이퍼 오픈 안내를 노출해야 하는지 여부. " +
                "오픈 시각이 지났고 아직 안내를 확인하지 않았으면 true. 주최자가 아니면 false",
        example = "false",
    )
    val hostRollingPaperNoticePending: Boolean,
    @Schema(description = "실시간 파티 일정. PAPER_ONLY면 null", nullable = true)
    val realtimeSchedule: UpcomingRealtimeScheduleResponse?,
    @Schema(
        description = "실시간 파티 현재 상태. PAPER_ONLY면 null",
        allowableValues = ["ROLLING_PAPER_OPEN", "LIVE_OPEN", "LIVE_ENDING", "LIVE_CLOSED", "ROLLING_PAPER_CLOSED"],
        example = "ROLLING_PAPER_OPEN",
        nullable = true,
    )
    val realtimeStatus: RealtimePartyStatus?,
    @Schema(
        description = "실시간 파티 입장 가능 여부. enterableFrom 이후 liveEndAt 이전에만 true. PAPER_ONLY면 false",
        example = "false",
    )
    val realtimeEnterable: Boolean,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "홈 다가오는 실시간 파티 일정")
data class UpcomingRealtimeScheduleResponse(
    @Schema(description = "실시간 파티 입장 가능 시작 시각", example = "2026-05-07T19:55:00")
    val enterableFrom: LocalDateTime,
    @Schema(description = "실시간 파티 예약 시작 시각", example = "2026-05-07T20:00:00")
    val liveStartAt: LocalDateTime,
    @Schema(
        description = "주최자가 실제로 파티를 시작한 시각. 아직 시작하지 않았으면 null. 상단 10분 타이머의 기준 시각",
        example = "2026-05-07T20:00:12",
        nullable = true,
    )
    val liveStartedAt: LocalDateTime?,
    @Schema(
        description =
            "실시간 파티가 닫히는 시각. 조기 종료했으면 실제 종료 시각, " +
                "시작했으면 liveStartedAt + 10분, 아직 시작 전이면 예약 시작 + 30분(시작 유예 마감)",
        example = "2026-05-07T20:10:12",
    )
    val liveEndAt: LocalDateTime,
)
