package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RealtimePartyEndService(
    private val partyRepository: PartyRepository,
) {
    fun startIfNotStarted(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ): RealtimePartyEndStartResult {
        val affected = partyRepository.startRealtimeEndingIfNotStarted(partyId, endingStartedAt)
        val party = findRealtimeParty(partyId)
        return RealtimePartyEndStartResult(
            affected = affected,
            party = party,
        )
    }

    fun startIfNotStartedOrNull(
        partyId: Long,
        endingStartedAt: LocalDateTime,
    ): RealtimePartyEndingSchedule? {
        val affected = partyRepository.startRealtimeEndingIfNotStarted(partyId, endingStartedAt)
        val party = findRealtimeParty(partyId)
        val actualEndingStartedAt = party.liveEndingStartedAt ?: return null
        return RealtimePartyEndingSchedule(
            partyId = party.id,
            endingStartedAt = actualEndingStartedAt,
            endedAt = requireNotNull(party.liveEndedAt()),
            startedNow = affected == 1,
        )
    }

    fun startDueAutomaticEndings(now: LocalDateTime) {
        partyRepository.startAutomaticRealtimeEndings(
            now = now,
            liveDurationMinutes = RealtimeParty.LIVE_DURATION_MINUTES,
            partyEndedAfterDays = Party.ENDED_AFTER_DAYS,
        )
    }

    fun findAutomaticEndSchedules(now: LocalDateTime): List<RealtimePartyAutomaticEndSchedule> =
        partyRepository
            .findRealtimePartiesWaitingAutomaticEnding(now.minusMinutes(RealtimeParty.LIVE_DURATION_MINUTES))
            .map { party ->
                RealtimePartyAutomaticEndSchedule(
                    partyId = party.id,
                    endingStartedAt = party.automaticEndingStartedAt(),
                )
            }

    fun findEndingTargets(now: LocalDateTime): List<RealtimePartyEndingSchedule> =
        partyRepository
            .findRealtimePartiesWithEndingStarted(now.minusDays(Party.ENDED_AFTER_DAYS))
            .map { party ->
                RealtimePartyEndingSchedule(
                    partyId = party.id,
                    endingStartedAt = requireNotNull(party.liveEndingStartedAt),
                    endedAt = requireNotNull(party.liveEndedAt()),
                    startedNow = false,
                )
            }

    private fun findRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        return Hibernate.unproxy(party) as RealtimeParty
    }
}

data class RealtimePartyEndStartResult(
    val affected: Int,
    val party: RealtimeParty,
)

data class RealtimePartyEndingSchedule(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val startedNow: Boolean = false,
)

data class RealtimePartyAutomaticEndSchedule(
    val partyId: Long,
    val endingStartedAt: LocalDateTime,
)
