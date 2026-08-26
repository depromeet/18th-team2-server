package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatLeaveExecutor
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class LeaveChatUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val chatLeaveExecutor: ChatLeaveExecutor,
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

        val payload = chatLeaveExecutor.execute(party, profile, userId)

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
}
