package com.team2.server.party.application.service

import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class RealtimePartyEndResultService(
    private val endingInfoPort: RealtimePartyEndingInfoPort,
    private val realtimePartyEndingEventPublisher: RealtimePartyEndingEventPublisher,
    private val phaseStore: PartyPhaseStore,
) {
    fun toResultAndPublish(
        startResult: RealtimePartyEndStartResult,
        now: LocalDateTime,
    ): RealtimePartyEndResult =
        RealtimePartyEndResult.from(startResult.party, endingInfoPort.get(startResult.party), now).also {
            phaseStore.forceSet(it.partyId, PartyPhase.END, it.endingStartedAt)
            if (startResult.affected == 1) realtimePartyEndingEventPublisher.publish(it)
        }
}
