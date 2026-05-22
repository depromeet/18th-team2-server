package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.application.service.RealtimePartyEndStartResult
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class StartRealtimePartyEndUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val realtimePartyEndService: RealtimePartyEndService,
    private val realtimePartyEndingEventPublisher: RealtimePartyEndingEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long,
    ): RealtimePartyEndResult {
        val now = LocalDateTime.now(clock)
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        validateHost(party, userId)
        return when (party.status(now)) {
            RealtimePartyStatus.LIVE_CLOSED -> throwRealtimePartyAlreadyEnded()
            RealtimePartyStatus.LIVE_ENDING -> existingOrPersistedAutomaticEnding(party)
            RealtimePartyStatus.LIVE_OPEN -> startIfAvailable(party, now)
            else -> throwRealtimePartyEndNotAvailable()
        }
    }

    private fun validateHost(
        party: RealtimeParty,
        userId: Long,
    ) {
        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
    }

    private fun startIfAvailable(
        party: RealtimeParty,
        now: LocalDateTime,
    ): RealtimePartyEndResult {
        if (party.liveEndingStartedAt != null || now.isBefore(party.hostEndAvailableAt())) {
            throwRealtimePartyEndNotAvailable()
        }
        return toResultAndPublish(realtimePartyEndService.startIfNotStarted(party.id, now))
    }

    private fun existingOrPersistedAutomaticEnding(party: RealtimeParty): RealtimePartyEndResult =
        if (party.liveEndingStartedAt == null) {
            toResultAndPublish(realtimePartyEndService.startIfNotStarted(party.id, party.automaticEndingStartedAt()))
        } else {
            RealtimePartyEndResult.from(party)
        }

    private fun toResultAndPublish(startResult: RealtimePartyEndStartResult): RealtimePartyEndResult {
        val result = RealtimePartyEndResult.from(startResult.party)
        if (startResult.affected == 1) realtimePartyEndingEventPublisher.publish(result)
        return result
    }

    private fun throwRealtimePartyAlreadyEnded(): Nothing =
        throw BusinessException(ErrorCode.REALTIME_PARTY_ALREADY_ENDED)

    private fun throwRealtimePartyEndNotAvailable(): Nothing =
        throw BusinessException(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE)
}
