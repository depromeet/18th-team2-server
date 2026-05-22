package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetRealtimePartyStateUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimePartyStateResult {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        validatePartyMember(party, userId, participantToken)
        return RealtimePartyStateResult.from(party, LocalDateTime.now(clock))
    }

    private fun validatePartyMember(
        party: RealtimeParty,
        userId: Long?,
        participantToken: String?,
    ) {
        if (isAuthenticatedMember(party, userId)) return
        if (participantToken == null) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        val profile = profileRepository.findByParticipantToken(participantToken)
        if (profile == null || profile.participant.party.id != party.id) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }

    private fun isAuthenticatedMember(
        party: RealtimeParty,
        userId: Long?,
    ): Boolean =
        userId == party.ownerId ||
            (userId != null && participantRepository.existsByPartyIdAndUserId(party.id, userId))
}
