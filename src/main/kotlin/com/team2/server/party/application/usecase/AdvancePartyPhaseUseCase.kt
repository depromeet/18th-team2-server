package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class AdvancePartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseStore: PartyPhaseStore,
    private val eventBroadcaster: RealtimePartyEventBroadcaster,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        currentPhase: PartyPhase,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        val nextPhase =
            ALLOWED_TRANSITIONS[currentPhase]
                ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        when (currentPhase) {
            PartyPhase.ENTRY -> {
                if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            PartyPhase.MUSIC -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }

        val advanced = phaseStore.advance(partyId, currentPhase, nextPhase, now)
        val entry = phaseStore.getEntry(partyId)
        val phase = entry?.phase ?: PartyPhase.ENTRY
        val phaseStartedAt = entry?.startedAt ?: party.startedAt

        if (advanced) {
            eventBroadcaster.broadcastPhaseChanged(partyId, phase, phaseStartedAt, now)
        }

        return PartyPhaseResult(
            partyId = partyId,
            phase = phase,
            phaseStartedAt = phaseStartedAt,
            serverNow = now,
        )
    }

    companion object {
        val ALLOWED_TRANSITIONS =
            mapOf(
                PartyPhase.ENTRY to PartyPhase.MUSIC,
                PartyPhase.MUSIC to PartyPhase.CANDLE,
            )
    }
}
