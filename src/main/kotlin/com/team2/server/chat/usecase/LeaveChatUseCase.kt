package com.team2.server.chat.usecase

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.UserLeftEventPayload
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyUseCase
import com.team2.server.party.application.usecase.StartRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class LeaveChatUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val participantService: ParticipantService,
    private val startRealtimePartyEndUseCase: StartRealtimePartyEndUseCase,
    private val chatSseGateway: ChatSseGateway,
) {
    @Transactional
    fun leave(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)

        participantService.leave(profile.participant)
        endPartyIfHostLeft(party, profile, userId)

        val payload =
            UserLeftEventPayload(
                nickname = profile.nickname,
                role = if (profile.participant.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )

        chatSseGateway.leave(profile.participantToken)
        chatSseGateway.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("user-left")
                .data(payload)
                .build(),
        )
    }

    private fun endPartyIfHostLeft(
        party: RealtimeParty,
        profile: RealtimeParticipantProfile,
        userId: Long?,
    ) {
        if (userId == party.ownerId || profile.participant.user?.id == party.ownerId) {
            startRealtimePartyEndUseCase(party.id, party.ownerId)
        }
    }
}
