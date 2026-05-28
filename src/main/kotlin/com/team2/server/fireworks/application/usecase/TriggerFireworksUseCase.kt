package com.team2.server.fireworks.application.usecase

import com.team2.server.chat.application.port.PartySseEventPublisher
import com.team2.server.fireworks.application.dto.FireworksPayload
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class TriggerFireworksUseCase(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val partySseEventPublisher: PartySseEventPublisher,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)

        val payload =
            FireworksPayload(
                partyId = partyId,
                participantId = profile.participant.id,
                nickname = profile.nickname,
            )

        partySseEventPublisher.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("fireworks")
                .data(payload)
                .build(),
        )
    }
}
