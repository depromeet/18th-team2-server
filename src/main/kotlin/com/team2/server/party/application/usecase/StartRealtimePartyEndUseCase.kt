package com.team2.server.party.application.usecase

import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.application.service.RealtimePartyEndResultService
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class StartRealtimePartyEndUseCase(
    private val partyService: PartyService,
    private val realtimePartyEndService: RealtimePartyEndService,
    private val endResultService: RealtimePartyEndResultService,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long,
    ): RealtimePartyEndResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        if (party.ownerId != userId) throwPartyBusiness(ErrorCode.PARTY_FORBIDDEN)
        return when (party.status(now)) {
            RealtimePartyStatus.LIVE_CLOSED -> throwPartyBusiness(ErrorCode.REALTIME_PARTY_ALREADY_ENDED)
            RealtimePartyStatus.LIVE_ENDING ->
                toResultAndPublish(
                    realtimePartyEndService.startIfNotStarted(
                        party.id,
                        party.liveEndingStartedAt ?: party.automaticEndingStartedAt(),
                        party.endingReason(now) ?: party.endingReasonForManualRequest(now),
                    ),
                )
            RealtimePartyStatus.LIVE_OPEN ->
                toResultAndPublish(
                    realtimePartyEndService.startIfNotStarted(
                        party.id,
                        now,
                        party.endingReasonForManualRequest(now),
                    ),
                )
            else -> throwPartyBusiness(ErrorCode.REALTIME_PARTY_INVALID_STATE)
        }
    }

    private fun toResultAndPublish(startResult: RealtimePartyEndStartResult) =
        endResultService.toResultAndPublish(startResult)
}
