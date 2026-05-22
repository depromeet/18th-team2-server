package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyCallerAccessService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyNextActionUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val partyCallerAccessService: PartyCallerAccessService,
    private val participantService: ParticipantService,
    private val partyInviteService: PartyInviteService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimePartyNextActionResult {
        val now = LocalDateTime.now(clock)
        partyCallerAccessService.validateCallerCanAccessParty(partyId, userId, participantToken)
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        if (party.status(now) != RealtimePartyStatus.LIVE_CLOSED) {
            throwRealtimePartyEndNotAvailable()
        }
        return when {
            userId == party.ownerId -> RealtimePartyNextActionResult.Host(partyId = party.id)
            else -> participantNextAction(party.id, userId, participantToken, now)
        }
    }

    private fun participantNextAction(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        now: LocalDateTime,
    ): RealtimePartyNextActionResult {
        val participant = participantService.requireCallerParticipant(partyId, userId, participantToken)
        if (participant.isCelebrant) return RealtimePartyNextActionResult.Host(partyId = partyId)
        val inviteToken = partyInviteService.findLatestUsableInviteToken(partyId, now)
        return RealtimePartyNextActionResult.Participant(
            inviteToken = inviteToken,
            rollingPaperWritten = participant.hasWrittenPaper,
        )
    }

    private fun throwRealtimePartyEndNotAvailable(): Nothing =
        throw BusinessException(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE)
}
