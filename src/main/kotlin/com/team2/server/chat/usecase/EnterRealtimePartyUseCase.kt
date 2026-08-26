package com.team2.server.chat.usecase

import com.team2.server.chat.application.dto.EnterRealtimePartyResult
import com.team2.server.chat.application.support.RealtimePartyEntryProfileResolver
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.usecase.ResolveRealtimePartyEndingInfoUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class EnterRealtimePartyUseCase(
    private val partyInviteService: PartyInviteService,
    private val profileResolver: RealtimePartyEntryProfileResolver,
    private val resolveRealtimePartyEndingInfoUseCase: ResolveRealtimePartyEndingInfoUseCase,
    private val clock: Clock,
) {
    @Transactional
    fun enter(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): EnterRealtimePartyResult {
        val now = LocalDateTime.now(clock)
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val realtimeParty = profileResolver.requireRealtime(invite.party)
        if (request.participantToken == null) profileResolver.validateNewEntry(realtimeParty, now)

        val entryProfile = profileResolver.resolve(realtimeParty, userId, request, now)
        val endingInfo = resolveRealtimePartyEndingInfoUseCase(realtimeParty, now)

        return EnterRealtimePartyResult(
            participantToken = entryProfile.participantToken,
            partyId = invite.party.id,
            startedAt = invite.party.startedAt,
            isCelebrant = entryProfile.isCelebrant,
            nickname = entryProfile.nickname,
            characterId = entryProfile.characterId,
            partyState = RealtimePartyStateResult.from(realtimeParty, now, endingInfo),
        )
    }
}
