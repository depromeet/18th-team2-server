package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.CharacterService
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEntryProfileResolver(
    private val participantService: ParticipantService,
    private val realtimeParticipantProfileService: RealtimeParticipantProfileService,
    private val characterService: CharacterService,
) {
    data class Result(
        val profile: RealtimeParticipantProfile,
        val character: Character,
    )

    fun resolve(
        party: RealtimeParty,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        now: LocalDateTime,
    ): Result {
        val character = characterService.requireCharacter(request.characterId)
        val profile =
            if (request.participantToken != null) {
                reenterByParticipantToken(
                    party = party,
                    participantToken = request.participantToken,
                    nickname = request.nickname,
                    character = character,
                    now = now,
                )
            } else {
                enterFirstTime(
                    party = party,
                    userId = userId,
                    nickname = request.nickname,
                    character = character,
                )
            }
        return Result(profile = profile, character = character)
    }

    private fun enterFirstTime(
        party: Party,
        userId: Long?,
        nickname: String,
        character: Character,
    ): RealtimeParticipantProfile {
        val user = participantService.resolveUser(userId)
        val participant = participantService.joinAnonymousOrMember(party, user)
        return realtimeParticipantProfileService.upsert(
            participant = participant,
            nickname = nickname,
            character = character,
            isHostNicknameLocked = participant.isCelebrant,
        )
    }

    private fun reenterByParticipantToken(
        party: RealtimeParty,
        participantToken: String,
        nickname: String,
        character: Character,
        now: LocalDateTime,
    ): RealtimeParticipantProfile {
        val profile =
            realtimeParticipantProfileService.requireByParticipantToken(participantToken, party.id)
        val reconnectableStatuses =
            listOf(
                RealtimePartyStatus.LIVE_OPEN,
                RealtimePartyStatus.LIVE_ENDING,
            )
        if (party.status(now) !in reconnectableStatuses) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        if (profile.participant.isCelebrant && profile.nickname != nickname) {
            throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
        }
        profile.nickname = nickname
        profile.character = character
        return profile
    }
}
