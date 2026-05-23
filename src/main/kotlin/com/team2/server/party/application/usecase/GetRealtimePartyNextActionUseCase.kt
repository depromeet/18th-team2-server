package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyNextActionUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
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
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        val participant =
            if (userId == party.ownerId) {
                null
            } else {
                participantService.requireCallerParticipant(party.id, userId, participantToken)
            }
        if (party.status(now) != RealtimePartyStatus.LIVE_CLOSED) {
            throwRealtimePartyEndNotAvailable()
        }
        return when {
            userId == party.ownerId -> RealtimePartyNextActionResult.Host(partyId = party.id)
            else -> participantNextAction(party.id, requireNotNull(participant), now)
        }
    }

    private fun participantNextAction(
        partyId: Long,
        participant: Participant,
        now: LocalDateTime,
    ): RealtimePartyNextActionResult {
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
