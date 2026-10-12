package com.team2.server.chat.application.support

import com.team2.server.chat.application.port.RealtimePartyEntryProfilePort
import com.team2.server.chat.application.port.RealtimePartyEntryProfileResult
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import org.hibernate.Hibernate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEntryProfileResolver(
    private val entryProfilePort: RealtimePartyEntryProfilePort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requireRealtime(party: Party): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        return Hibernate.unproxy(party) as RealtimeParty
    }

    fun validateNewEntry(
        party: RealtimeParty,
        now: LocalDateTime,
    ) {
        if (!party.isEnterable(now)) {
            log.warn(
                "Realtime party entry is out of enterable window. partyId={} status={} " +
                    "startedAt={} liveEndingStartedAt={} now={}",
                party.id,
                party.status(now),
                party.startedAt,
                party.liveEndingStartedAt,
                now,
            )
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
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
