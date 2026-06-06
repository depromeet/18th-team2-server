package com.team2.server.chat.application.support

import com.team2.server.chat.application.port.RealtimePartyEntryProfilePort
import com.team2.server.chat.application.port.RealtimePartyEntryProfileResult
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.hibernate.Hibernate
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEntryProfileResolver(
    private val entryProfilePort: RealtimePartyEntryProfilePort,
) {
    fun requireRealtime(party: Party): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        return Hibernate.unproxy(party) as RealtimeParty
    }

    fun validateNewEntry(
        party: RealtimeParty,
        now: LocalDateTime,
    ) {
        if (party.status(now) != RealtimePartyStatus.LIVE_OPEN) throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
    }

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
