package com.team2.server.party.application.service

import com.team2.server.party.application.port.BurstGameStartPort
import com.team2.server.party.application.port.CandleBlowStartPort
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PartyPhaseTransitionService(
    private val phaseStore: PartyPhaseStore,
    private val eventBroadcaster: RealtimePartyEventBroadcaster,
    private val burstGameStartPort: BurstGameStartPort,
    private val candleBlowStartPort: CandleBlowStartPort,
) {
    fun advance(
        partyId: Long,
        currentPhase: PartyPhase,
        nextPhase: PartyPhase,
        now: LocalDateTime,
        userId: Long?,
        participantToken: String?,
    ): Boolean {
        val advanced =
            if (nextPhase == PartyPhase.BURST) {
                phaseStore.advance(partyId, currentPhase, nextPhase, now) {
                    burstGameStartPort.start(partyId, userId, participantToken)
                }
            } else {
                phaseStore.advance(partyId, currentPhase, nextPhase, now)
            }
        if (advanced) {
            eventBroadcaster.broadcastPhaseChanged(partyId, nextPhase, now, now)
            startCandleBlowIfNeeded(partyId, nextPhase, now)
        }
        return advanced
    }

    fun getEntry(partyId: Long): PartyPhaseStore.PhaseEntry? = phaseStore.getEntry(partyId)

    private fun startCandleBlowIfNeeded(
        partyId: Long,
        nextPhase: PartyPhase,
        now: LocalDateTime,
    ) {
        if (nextPhase == PartyPhase.CANDLE) {
            candleBlowStartPort.start(partyId, now)
        }
    }
}
