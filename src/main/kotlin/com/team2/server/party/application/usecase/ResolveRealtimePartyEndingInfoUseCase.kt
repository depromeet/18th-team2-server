package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ResolveRealtimePartyEndingInfoUseCase(
    private val endingInfoPort: RealtimePartyEndingInfoPort,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        party: RealtimeParty,
        now: LocalDateTime,
    ): RealtimePartyEndingInfo = endingInfoPort.get(party, now)
}
