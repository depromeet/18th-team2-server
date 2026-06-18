package com.team2.server.chat.infrastructure.party

import com.team2.server.chat.application.port.RealtimePartyEntryProfilePort
import com.team2.server.chat.application.port.RealtimePartyEntryProfileResult
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
class PartyRealtimePartyEntryProfileAdapter(
    private val participantService: ParticipantService,
    private val realtimeParticipantProfileService: RealtimeParticipantProfileService,
    private val characterService: CharacterService,
) : RealtimePartyEntryProfilePort {
    override fun resolve(
        party: RealtimeParty,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        now: LocalDateTime,
    ): RealtimePartyEntryProfileResult {
        val character = characterService.requireCharacter(request.characterId)
        val profile =
            if (request.participantToken != null) {
                reenterByParticipantToken(party, request.participantToken, request.nickname, character, now)
            } else {
                enterFirstTime(party, userId, request.nickname, character)
            }
        return profile.toResult()
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
        val profile = realtimeParticipantProfileService.requireForReentryByParticipantToken(participantToken, party.id)
        if (party.status(now) !in RECONNECTABLE_STATUSES) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        if (profile.participant.isCelebrant && profile.nickname != nickname) {
            throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
        }
        profile.nickname = nickname
        profile.character = character
        return profile
    }

    private fun RealtimeParticipantProfile.toResult(): RealtimePartyEntryProfileResult =
        RealtimePartyEntryProfileResult(
            participantToken = participantToken,
            isCelebrant = participant.isCelebrant,
            nickname = nickname,
            characterId = character?.id,
        )

    private companion object {
        val RECONNECTABLE_STATUSES: Set<RealtimePartyStatus> =
            setOf(
                RealtimePartyStatus.LIVE_OPEN,
            )
    }
}
