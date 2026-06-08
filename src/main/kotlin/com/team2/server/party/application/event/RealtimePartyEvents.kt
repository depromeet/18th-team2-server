package com.team2.server.party.application.event

import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import java.time.LocalDateTime

data class RealtimePartyCreatedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)

data class RealtimePartyHostEnteredEvent(
    val partyId: Long,
    val hostEnteredAt: LocalDateTime,
)

data class RealtimePartyEndingStartedEvent(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val endingReason: RealtimePartyEndingReason,
    val hostNickname: String,
)

data class RealtimePartyBurstGameStartedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)

data class RealtimePartyBurstGameEndedEvent(
    val partyId: Long,
    val endedAt: LocalDateTime,
)
