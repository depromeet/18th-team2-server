package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResolveRealtimeParticipantProfileUseCase(
    private val profileService: RealtimeParticipantProfileService,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): RealtimeParticipantProfile = profileService.resolveProfile(partyId, userId, participantToken)
}
