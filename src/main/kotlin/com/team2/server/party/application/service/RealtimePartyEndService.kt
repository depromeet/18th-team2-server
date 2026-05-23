package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimeHostEndAvailableSchedule
import com.team2.server.party.application.dto.RealtimePartyEndRecoverySchedules
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
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
    ): RealtimeEndingScheduleTarget? {
        val affected = partyRepository.startRealtimeEndingIfNotStarted(partyId, endingStartedAt)
        val party = findRealtimeParty(partyId)
        val actualEndingStartedAt = party.liveEndingStartedAt ?: return null
        return RealtimeEndingScheduleTarget(
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

    fun findRecoverySchedules(now: LocalDateTime): RealtimePartyEndRecoverySchedules {
        val waitingParties =
            partyRepository.findRealtimePartiesWaitingAutomaticEnding(
                now.minusMinutes(RealtimeParty.LIVE_DURATION_MINUTES),
            )
        return RealtimePartyEndRecoverySchedules(
            hostEndAvailableSchedules =
                waitingParties
                    .filter { party -> party.status(now) == RealtimePartyStatus.LIVE_OPEN }
                    .map { party ->
                        RealtimeHostEndAvailableSchedule(
                            partyId = party.id,
                            startedAt = party.startedAt,
                        )
                    },
            automaticEndSchedules =
                waitingParties.map { party ->
                    RealtimeAutomaticEndSchedule(
                        partyId = party.id,
                        endingStartedAt = party.automaticEndingStartedAt(),
                    )
                },
        )
    }

    fun findEndingTargets(now: LocalDateTime): List<RealtimeEndingScheduleTarget> =
        partyRepository
            .findRealtimePartiesWithEndingStarted(now.minusDays(Party.ENDED_AFTER_DAYS))
            .map { party ->
                RealtimeEndingScheduleTarget(
                    partyId = party.id,
                    endingStartedAt = requireNotNull(party.liveEndingStartedAt),
                    endedAt = requireNotNull(party.liveEndedAt()),
                    startedNow = false,
                )
            }

    fun canNotifyHostEndAvailable(
        partyId: Long,
        now: LocalDateTime,
    ): Boolean {
        val party = findRealtimePartyOrNull(partyId) ?: return false
        return party.liveEndingStartedAt == null && party.status(now) == RealtimePartyStatus.LIVE_OPEN
    }

    private fun findRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        return Hibernate.unproxy(party) as RealtimeParty
    }

    private fun findRealtimePartyOrNull(partyId: Long): RealtimeParty? {
        val party = partyRepository.findPartyById(partyId)
        return when {
            party == null || party.partyOption != PartyOption.REALTIME -> null
            else -> Hibernate.unproxy(party) as RealtimeParty
        }
    }
}
