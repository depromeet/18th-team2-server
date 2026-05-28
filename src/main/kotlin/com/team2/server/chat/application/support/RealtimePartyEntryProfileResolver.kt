package com.team2.server.chat.application.support

import com.team2.server.chat.application.port.RealtimePartyEntryProfilePort
import com.team2.server.chat.application.port.RealtimePartyEntryProfileResult
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEntryProfileResolver(
    private val entryProfilePort: RealtimePartyEntryProfilePort,
) {
    fun resolve(
        party: RealtimeParty,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        now: LocalDateTime,
    ): RealtimePartyEntryProfileResult =
        entryProfilePort.resolve(
            party = party,
            userId = userId,
            request = request,
            now = now,
        )
}
