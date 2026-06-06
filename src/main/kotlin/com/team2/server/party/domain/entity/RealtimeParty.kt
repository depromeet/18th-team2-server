package com.team2.server.party.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "realtime_party")
@DiscriminatorValue("REALTIME")
class RealtimeParty(
    ownerId: Long,
    name: String? = null,
    celebrantNickname: String? = null,
    purpose: PartyPurpose = PartyPurpose.BIRTHDAY,
    startedAt: LocalDateTime,
    @Column(name = "live_ending_started_at")
    var liveEndingStartedAt: LocalDateTime? = null,
    @Column(name = "host_entered_at")
    var hostEnteredAt: LocalDateTime? = null,
) : Party(ownerId, name, celebrantNickname, startedAt, purpose) {
    override val partyOption: PartyOption get() = PartyOption.REALTIME

    override fun hostViewableAt(): LocalDateTime = effectiveEndingStartedAt()

    fun automaticEndingStartedAt(): LocalDateTime = startedAt.plusMinutes(LIVE_DURATION_MINUTES)

    fun effectiveEndingStartedAt(): LocalDateTime = liveEndingStartedAt ?: automaticEndingStartedAt()

    fun effectiveLiveEndedAt(): LocalDateTime = effectiveEndingStartedAt().plusSeconds(LIVE_END_COUNTDOWN_SECONDS)

    fun liveEndedAt(): LocalDateTime? = liveEndingStartedAt?.plusSeconds(LIVE_END_COUNTDOWN_SECONDS)

    fun endingReason(): RealtimePartyEndingReason? =
        liveEndingStartedAt?.let {
            if (it.isBefore(automaticEndingStartedAt())) {
                RealtimePartyEndingReason.HOST_REQUEST
            } else {
                RealtimePartyEndingReason.TIME_LIMIT_REACHED
            }
        }

    fun endingReason(now: LocalDateTime): RealtimePartyEndingReason? =
        endingReason()
            ?: if (!now.isBefore(automaticEndingStartedAt())) {
                RealtimePartyEndingReason.TIME_LIMIT_REACHED
            } else {
                null
            }

    fun isLiveOpen(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !now.isBefore(startedAt) && now.isBefore(effectiveEndingStartedAt())

    fun status(now: LocalDateTime = LocalDateTime.now()): RealtimePartyStatus =
        when {
            isEnded(now) -> RealtimePartyStatus.ROLLING_PAPER_CLOSED
            now.isBefore(startedAt) -> RealtimePartyStatus.ROLLING_PAPER_OPEN
            now.isBefore(effectiveEndingStartedAt()) -> RealtimePartyStatus.LIVE_OPEN
            now.isBefore(effectiveLiveEndedAt()) -> RealtimePartyStatus.LIVE_ENDING
            else -> RealtimePartyStatus.LIVE_CLOSED
        }

    companion object {
        const val LIVE_DURATION_MINUTES: Long = 10
        const val LIVE_END_COUNTDOWN_SECONDS: Long = 60
        const val ENTERABLE_BEFORE_MINUTES: Long = 5
        const val MAX_PARTICIPANTS: Int = 14
    }
}

enum class RealtimePartyStatus {
    ROLLING_PAPER_OPEN,
    LIVE_OPEN,
    LIVE_ENDING,
    LIVE_CLOSED,
    ROLLING_PAPER_CLOSED,
}

enum class RealtimePartyEndingReason {
    HOST_REQUEST,
    TIME_LIMIT_REACHED,
}
