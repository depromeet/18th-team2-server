package com.team2.server.party.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "realtime_party")
class RealtimeParty(
    ownerId: Long,
    name: String? = null,
    celebrantNickname: String? = null,
    purpose: PartyPurpose = PartyPurpose.BIRTHDAY,
    startedAt: LocalDateTime,
) : Party(ownerId, name, celebrantNickname, startedAt, purpose, PartyOption.REALTIME) {
    fun status(now: LocalDateTime = LocalDateTime.now()): RealtimePartyStatus {
        val liveStart = startedAt
        val liveEnd = liveStart.plusMinutes(LIVE_DURATION_MINUTES)
        val endedTime = createdAt.plusDays(Party.ENDED_AFTER_DAYS)
        return when {
            now >= endedTime -> RealtimePartyStatus.ROLLING_PAPER_CLOSED
            now >= liveEnd -> RealtimePartyStatus.LIVE_CLOSED
            now >= liveStart -> RealtimePartyStatus.LIVE_OPEN
            else -> RealtimePartyStatus.ROLLING_PAPER_OPEN
        }
    }

    companion object {
        const val LIVE_DURATION_MINUTES: Long = 10
        const val ENTERABLE_BEFORE_MINUTES: Long = 5
    }
}

enum class RealtimePartyStatus {
    ROLLING_PAPER_OPEN,
    LIVE_OPEN,
    LIVE_CLOSED,
    ROLLING_PAPER_CLOSED,
}
