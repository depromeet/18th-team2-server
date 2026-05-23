package com.team2.server.party.application.event

import java.time.LocalDateTime

data class RealtimePartyCreatedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)

data class RealtimePartyHostEndAvailableScheduleRequestedEvent(
    val partyId: Long,
    val startedAt: LocalDateTime,
)

data class RealtimePartyBurstGameEndedEvent(
    val partyId: Long,
    val endedAt: LocalDateTime,
)

data class RealtimePartyEndingStartedEvent(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
)
