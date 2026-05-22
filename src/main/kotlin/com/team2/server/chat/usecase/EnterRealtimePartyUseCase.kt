package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.service.CharacterService
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class EnterRealtimePartyUseCase(
    private val partyInviteService: PartyInviteService,
    private val participantService: ParticipantService,
    private val realtimeParticipantProfileService: RealtimeParticipantProfileService,
    private val characterService: CharacterService,
    private val clock: Clock,
) {
    data class EnterResult(
        val participantToken: String,
        val partyId: Long,
        val startedAt: LocalDateTime,
        val isCelebrant: Boolean,
        val nickname: String,
        val characterId: Long?,
        val partyState: RealtimePartyStateResult,
    )

    @Transactional
    fun enter(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): EnterResult {
        val now = LocalDateTime.now(clock)
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val realtimeParty = validateInvite(invite.party)
        val reentry = request.participantToken != null
        if (!reentry) {
            validateNewEnterable(realtimeParty, now)
        }

        val character = characterService.requireCharacter(request.characterId)

        val profile =
            if (reentry) {
                reenterByParticipantToken(
                    party = realtimeParty,
                    participantToken = requireNotNull(request.participantToken),
                    nickname = request.nickname,
                    character = character,
                    now = now,
                )
            } else {
                val participant = participantService.joinAnonymousOrMember(invite.party, userId)
                realtimeParticipantProfileService.upsert(
                    participant = participant,
                    nickname = request.nickname,
                    character = character,
                    isHostNicknameLocked = participant.isCelebrant,
                )
            }

        return EnterResult(
            participantToken = profile.participantToken,
            partyId = invite.party.id,
            startedAt = invite.party.startedAt,
            isCelebrant = profile.participant.isCelebrant,
            nickname = profile.nickname,
            characterId = character.id,
            partyState = RealtimePartyStateResult.from(realtimeParty, now),
        )
    }

    private fun validateInvite(party: Party): RealtimeParty {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }

    private fun validateNewEnterable(
        realtimeParty: RealtimeParty,
        now: LocalDateTime,
    ) {
        if (realtimeParty.status(now) != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
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
            throwChatNotActive()
        }
        if (profile.participant.isCelebrant && profile.nickname != nickname) {
            throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
        }
        profile.nickname = nickname
        profile.character = character
        return profile
    }

    private fun throwChatNotActive(): Nothing = throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
}
