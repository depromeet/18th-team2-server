package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CharacterResult
import com.team2.server.party.application.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.infrastructure.CharacterImageResolver
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetMyRealtimeProfileUseCase(
    private val partyInviteService: PartyInviteService,
    private val participantService: ParticipantService,
    private val profileService: RealtimeParticipantProfileService,
    private val userRepository: UserRepository,
    private val characterImageResolver: CharacterImageResolver,
) {
    @Transactional
    fun invoke(
        inviteToken: String,
        userId: Long,
    ): ParticipantRealtimeProfileResult {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = invite.party
        validateUsableRealtimeParty(party, now)
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(party, user)
        val profile = profileService.findByParticipant(participant)
        val character =
            profile?.character?.let {
                CharacterResult(
                    characterId = it.id,
                    name = it.name,
                    characterImageUrl = characterImageResolver.resolve(it),
                    characterThumbnailImageUrl = null,
                )
            }
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile?.nickname,
            character = character,
        )
    }

    private fun validateUsableRealtimeParty(
        party: Party,
        now: LocalDateTime,
    ) {
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
    }
}
