package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "실시간 파티 종료 카운트다운 시작 응답")
data class RealtimePartyEndResult(
    @Schema(description = "파티 ID", example = "1")
    val partyId: Long,
    @Schema(description = "종료 카운트다운 시작 시각", example = "2026-05-19T20:10:00")
    val endingStartedAt: LocalDateTime,
    @Schema(description = "실시간 라이브 종료 시각", example = "2026-05-19T20:11:00")
    val endedAt: LocalDateTime,
) {
    companion object {
        fun from(party: RealtimeParty): RealtimePartyEndResult =
            RealtimePartyEndResult(
                partyId = party.id,
                endingStartedAt = requireNotNull(party.liveEndingStartedAt),
                endedAt = requireNotNull(party.liveEndedAt()),
            )
    }
}

@Schema(description = "실시간 파티 상태 복구 조회 응답")
data class RealtimePartyStateResult(
    @Schema(description = "파티 ID", example = "1")
    val partyId: Long,
    @Schema(
        description = "실시간 파티 상태",
        example = "LIVE_OPEN",
        allowableValues = ["ROLLING_PAPER_OPEN", "LIVE_OPEN", "LIVE_ENDING", "LIVE_CLOSED", "ROLLING_PAPER_CLOSED"],
    )
    val status: RealtimePartyStatus,
    @Schema(description = "실시간 라이브 시작 시각", example = "2026-05-19T20:00:00")
    val liveStartAt: LocalDateTime,
    @Schema(description = "종료 카운트다운 시작 시각. 아직 시작되지 않았으면 null", nullable = true, example = "2026-05-19T20:10:00")
    val endingStartedAt: LocalDateTime?,
    @Schema(description = "실시간 라이브 종료 시각", example = "2026-05-19T20:11:00")
    val endedAt: LocalDateTime,
) {
    companion object {
        fun from(
            party: RealtimeParty,
            now: LocalDateTime,
        ): RealtimePartyStateResult {
            val status = party.status(now)
            val endingStartedAt =
                party.liveEndingStartedAt
                    ?: if (status == RealtimePartyStatus.LIVE_ENDING || status == RealtimePartyStatus.LIVE_CLOSED) {
                        party.effectiveEndingStartedAt()
                    } else {
                        null
                    }
            return RealtimePartyStateResult(
                partyId = party.id,
                status = status,
                liveStartAt = party.startedAt,
                endingStartedAt = endingStartedAt,
                endedAt = party.effectiveLiveEndedAt(),
            )
        }
    }
}

@Schema(
    description = "실시간 파티 종료 후 다음 행동 조회 응답",
    oneOf = [RealtimePartyNextActionResult.Host::class, RealtimePartyNextActionResult.Participant::class],
)
sealed interface RealtimePartyNextActionResult {
    @get:Schema(
        description = "다음 행동 타입",
        example = "PARTICIPANT_ROLLING_PAPER_WRITE",
        allowableValues = ["HOST_ROLLING_PAPER_LIST", "PARTICIPANT_ROLLING_PAPER_WRITE"],
    )
    val type: String

    @Schema(description = "주최자용 다음 행동 응답")
    data class Host(
        @Schema(description = "롤링페이퍼 목록으로 이동할 파티 ID", example = "1")
        val partyId: Long,
    ) : RealtimePartyNextActionResult {
        override val type: String = "HOST_ROLLING_PAPER_LIST"
    }

    @Schema(description = "참가자용 다음 행동 응답")
    data class Participant(
        @Schema(description = "롤링페이퍼 작성 화면 진입에 사용할 유효 초대 토큰", example = "exampletoken0000")
        val inviteToken: String,
        @Schema(description = "현재 참가자의 롤링페이퍼 작성 완료 여부", example = "false")
        val rollingPaperWritten: Boolean,
    ) : RealtimePartyNextActionResult {
        override val type: String = "PARTICIPANT_ROLLING_PAPER_WRITE"
    }
}
