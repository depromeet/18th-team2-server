package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.PartyQueryService
import com.team2.server.party.domain.entity.RealtimeParty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ResolveChatOpenRealtimePartyUseCase(
    private val partyQueryService: PartyQueryService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun invoke(partyId: Long): RealtimeParty {
        val party = partyQueryService.requireRealtimeParty(partyId)
        val now = LocalDateTime.now(clock)
        if (!party.isChatOpen(now)) {
            log.warn(
                "Realtime party chat is not open. partyId={} status={} " +
                    "startedAt={} liveEndingStartedAt={} now={} clockZone={}",
                partyId,
                party.status(now),
                party.startedAt,
                party.liveEndingStartedAt,
                now,
                clock.zone,
            )
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return party
    }
}
