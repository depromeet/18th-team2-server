package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyNextActionUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val partyInviteRepository: PartyInviteRepository,
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
        if (party.status(now) != RealtimePartyStatus.LIVE_CLOSED) {
            throwRealtimePartyEndNotAvailable()
        }
        if (userId == party.ownerId) {
            return RealtimePartyNextActionResult.Host(partyId = party.id)
        }
        val participant = resolveParticipant(party, userId, participantToken)
        val invite =
            partyInviteRepository.findByPartyIdAndExpiresAtAfter(party.id, now)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        return RealtimePartyNextActionResult.Participant(
            inviteToken = invite.token,
            rollingPaperWritten = participant.hasWrittenPaper,
        )
    }

    private fun resolveParticipant(
        party: RealtimeParty,
        userId: Long?,
        participantToken: String?,
    ): Participant {
        if (userId != null) {
            return participantRepository.findByPartyIdAndUserId(party.id, userId)
                ?: throwForbidden()
        }
        if (participantToken != null) {
            val profile =
                profileRepository.findByParticipantToken(participantToken)
                    ?: throwForbidden()
            if (profile.participant.party.id != party.id) {
                throwForbidden()
            }
            return profile.participant
        }
        throwUnauthorized()
    }

    private fun throwRealtimePartyEndNotAvailable(): Nothing =
        throw BusinessException(ErrorCode.REALTIME_PARTY_END_NOT_AVAILABLE)

    private fun throwForbidden(): Nothing = throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

    private fun throwUnauthorized(): Nothing = throw BusinessException(ErrorCode.UNAUTHORIZED)
}
