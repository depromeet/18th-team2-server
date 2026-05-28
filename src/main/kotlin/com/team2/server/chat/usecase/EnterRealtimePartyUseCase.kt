package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.usecase.MarkRealtimePartyHostEnteredUseCase
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
    private val profileResolver: RealtimePartyEntryProfileResolver,
    private val markRealtimePartyHostEnteredUseCase: MarkRealtimePartyHostEnteredUseCase,
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

        val entryProfile = profileResolver.resolve(realtimeParty, userId, request, now)
        val profile = entryProfile.profile
        markHostEnteredIfNeeded(realtimeParty, profile, now)

        return EnterResult(
            participantToken = profile.participantToken,
            partyId = invite.party.id,
            startedAt = invite.party.startedAt,
            isCelebrant = profile.participant.isCelebrant,
            nickname = profile.nickname,
            characterId = entryProfile.character.id,
            partyState = RealtimePartyStateResult.from(realtimeParty, now),
        )
    }

    private fun markHostEnteredIfNeeded(
        party: RealtimeParty,
        profile: RealtimeParticipantProfile,
        now: LocalDateTime,
    ) {
        if (!profile.participant.isCelebrant) return
        party.hostEnteredAt = markRealtimePartyHostEnteredUseCase(party.id, now) ?: return
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
}
