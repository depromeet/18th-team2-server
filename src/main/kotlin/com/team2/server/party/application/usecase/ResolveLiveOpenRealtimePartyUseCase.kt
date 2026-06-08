package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ResolveLiveOpenRealtimePartyUseCase(
    private val partyService: PartyService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun invoke(partyId: Long): RealtimeParty {
        val party = partyService.requireRealtimeParty(partyId)
        val now = LocalDateTime.now(clock)
        val status = party.status(now)
        if (status !in ACTIVE_STATUSES) {
            log.warn(
                "Realtime party is not active. partyId={} status={} " +
                    "startedAt={} liveEndingStartedAt={} now={} clockZone={}",
                partyId,
                status,
                party.startedAt,
                party.liveEndingStartedAt,
                now,
                clock.zone,
            )
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return party
    }

    private companion object {
        private val ACTIVE_STATUSES =
            setOf(
                RealtimePartyStatus.LIVE_OPEN,
                RealtimePartyStatus.LIVE_ENDING,
            )
    }
}
