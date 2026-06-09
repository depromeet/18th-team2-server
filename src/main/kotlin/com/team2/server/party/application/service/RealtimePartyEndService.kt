package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimeAutomaticEndSchedule
import com.team2.server.party.application.dto.RealtimeEndingScheduleTarget
import com.team2.server.party.application.dto.RealtimePartyEndRecoverySchedules
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RealtimePartyEndService(
    private val partyRepository: PartyRepository,
    private val endingInfoPort: RealtimePartyEndingInfoPort,
) {
    fun startIfNotStarted(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
    ): RealtimePartyEndStartResult {
        val affected = partyRepository.startRealtimeEndingIfNotStarted(partyId, endingStartedAt, endingReason)
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
        val affected =
            partyRepository.startRealtimeEndingIfNotStarted(
                partyId,
                endingStartedAt,
                RealtimePartyEndingReason.TIME_LIMIT_REACHED,
            )
        val party = findRealtimeParty(partyId)
        val actualEndingStartedAt = party.liveEndingStartedAt ?: return null
        val endingInfo = endingInfoPort.get(party)
        return RealtimeEndingScheduleTarget(
            partyId = party.id,
            endingStartedAt = actualEndingStartedAt,
            endedAt = requireNotNull(party.liveEndedAt()),
            endingReason = requireNotNull(endingInfo.endingReason),
            hostNickname = endingInfo.hostNickname,
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
                val endingInfo = endingInfoPort.get(party)
                RealtimeEndingScheduleTarget(
                    partyId = party.id,
                    endingStartedAt = requireNotNull(party.liveEndingStartedAt),
                    endedAt = requireNotNull(party.liveEndedAt()),
                    endingReason = requireNotNull(endingInfo.endingReason),
                    hostNickname = endingInfo.hostNickname,
                    startedNow = false,
                )
            }

    private fun findRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        return Hibernate.unproxy(party) as RealtimeParty
    }
}
