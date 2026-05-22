package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import java.time.LocalDateTime

data class RealtimePartyEndStatusResult(
    val canEnd: Boolean,
    val availableAt: LocalDateTime,
    val endingStartedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
)

data class RealtimePartyEndResult(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
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

data class RealtimePartyStateResult(
    val partyId: Long,
    val status: RealtimePartyStatus,
    val liveStartAt: LocalDateTime,
    val endingStartedAt: LocalDateTime?,
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

sealed interface RealtimePartyNextActionResult {
    val type: String

    data class Host(
        val partyId: Long,
    ) : RealtimePartyNextActionResult {
        override val type: String = "HOST_ROLLING_PAPER_LIST"
    }

    data class Participant(
        val inviteToken: String,
        val rollingPaperWritten: Boolean,
    ) : RealtimePartyNextActionResult {
        override val type: String = "PARTICIPANT_ROLLING_PAPER_WRITE"
    }
}

data class RealtimeEndingScheduleTarget(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val startedNow: Boolean = false,
)

data class RealtimeAutomaticEndSchedule(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
)

data class RealtimePartyEndRecoveryResult(
    val automaticEndSchedules: List<RealtimeAutomaticEndSchedule>,
    val endingTargets: List<RealtimeEndingScheduleTarget>,
)
