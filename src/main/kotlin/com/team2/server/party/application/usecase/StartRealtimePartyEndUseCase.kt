package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.event.RealtimePartyEndingEventPublisher
import com.team2.server.party.application.port.BurstGameCompletionReader
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
    private val burstGameCompletionReader: BurstGameCompletionReader,
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
        if (party.ownerId != userId) throwBusiness(ErrorCode.PARTY_FORBIDDEN)
        return when (party.status(now)) {
            RealtimePartyStatus.LIVE_CLOSED -> throwBusiness(ErrorCode.REALTIME_PARTY_ALREADY_ENDED)
            RealtimePartyStatus.LIVE_ENDING -> existingOrPersistedAutomaticEnding(party)
            RealtimePartyStatus.LIVE_OPEN ->
                if (party.canStartManually(now)) {
                    toResultAndPublish(realtimePartyEndService.startIfNotStarted(party.id, now))
                } else {
                    throwBusiness(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE)
                }
            else -> throwBusiness(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE)
        }
    }

    private fun RealtimeParty.canStartManually(now: LocalDateTime): Boolean =
        liveEndingStartedAt == null &&
            (!now.isBefore(hostEndAvailableAt()) || burstGameCompletionReader.isEnded(id))

    private fun existingOrPersistedAutomaticEnding(party: RealtimeParty): RealtimePartyEndResult =
        party.liveEndingStartedAt?.let { RealtimePartyEndResult.from(party) }
            ?: toResultAndPublish(realtimePartyEndService.startIfNotStarted(party.id, party.automaticEndingStartedAt()))

    private fun toResultAndPublish(startResult: RealtimePartyEndStartResult): RealtimePartyEndResult =
        RealtimePartyEndResult.from(startResult.party).also {
            if (startResult.affected == 1) realtimePartyEndingEventPublisher.publish(it)
        }

    private fun throwBusiness(errorCode: ErrorCode): Nothing = throw BusinessException(errorCode)
}
