package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.port.BurstGameStartPort
import com.team2.server.party.application.port.CandleBlowStartPort
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
    private val burstGameStartPort: BurstGameStartPort,
    private val candleBlowStartPort: CandleBlowStartPort,
    private val clock: Clock,
) {
    @Transactional
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
            PartyPhase.MUSIC,
            PartyPhase.CANDLE,
            -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }

        if (nextPhase == PartyPhase.BURST && currentStoredPhase(partyId) == currentPhase) {
            burstGameStartPort.start(partyId, userId, participantToken)
        }
        val advanced = phaseStore.advance(partyId, currentPhase, nextPhase, now)

        if (advanced) {
            eventBroadcaster.broadcastPhaseChanged(partyId, nextPhase, now, now)
            if (nextPhase == PartyPhase.CANDLE) {
                candleBlowStartPort.start(partyId, now)
            }
            return PartyPhaseResult(
                partyId = partyId,
                phase = nextPhase,
                phaseStartedAt = now,
                serverNow = now,
            )
        }

        val entry = phaseStore.getEntry(partyId)
        return PartyPhaseResult(
            partyId = partyId,
            phase = entry?.phase ?: PartyPhase.ENTRY,
            phaseStartedAt = entry?.startedAt ?: party.startedAt,
            serverNow = now,
        )
    }

    private fun currentStoredPhase(partyId: Long): PartyPhase = phaseStore.getEntry(partyId)?.phase ?: PartyPhase.ENTRY

    companion object {
        val ALLOWED_TRANSITIONS =
            mapOf(
                PartyPhase.ENTRY to PartyPhase.MUSIC,
                PartyPhase.MUSIC to PartyPhase.CANDLE,
                PartyPhase.CANDLE to PartyPhase.BURST,
            )
    }
}
