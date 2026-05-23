package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndStatusResult
import com.team2.server.party.application.port.BurstGameCompletionReader
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyEndStatusUseCase(
    private val partyService: PartyService,
    private val burstGameCompletionReader: BurstGameCompletionReader,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long,
    ): RealtimePartyEndStatusResult {
        val party = partyService.requireRealtimeParty(partyId)
        validateHost(party, userId)
        val now = LocalDateTime.now(clock)
        val status = party.status(now)
        val endingStartedAt =
            party.liveEndingStartedAt
                ?: if (status == RealtimePartyStatus.LIVE_ENDING || status == RealtimePartyStatus.LIVE_CLOSED) {
                    party.effectiveEndingStartedAt()
                } else {
                    null
                }
        return RealtimePartyEndStatusResult(
            canEnd = canEnd(party, now),
            availableAt = party.hostEndAvailableAt(),
            endingStartedAt = endingStartedAt,
            endedAt = endingStartedAt?.plusSeconds(RealtimeParty.LIVE_END_COUNTDOWN_SECONDS),
        )
    }

    private fun validateHost(
        party: RealtimeParty,
        userId: Long,
    ) {
        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
    }

    private fun canEnd(
        party: RealtimeParty,
        now: LocalDateTime,
    ): Boolean =
        party.liveEndingStartedAt == null &&
            party.status(now) == RealtimePartyStatus.LIVE_OPEN &&
            (
                !now.isBefore(party.hostEndAvailableAt()) ||
                    burstGameCompletionReader.isCompleted(party.id, now)
            )
}
