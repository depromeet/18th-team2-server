package com.team2.server.fireworks.api

import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompSessionUserRegistry
import com.team2.server.fireworks.api.dto.FireworksSocketRequest
import com.team2.server.fireworks.application.usecase.TriggerFireworksUseCase
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class FireworksSocketController(
    private val triggerFireworksUseCase: TriggerFireworksUseCase,
    private val stompSessionUserRegistry: StompSessionUserRegistry,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @MessageMapping("/parties/{partyId}/fireworks")
    fun triggerFireworks(
        @DestinationVariable partyId: Long,
        @Valid @Payload request: FireworksSocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val payload =
            triggerFireworksUseCase.invoke(
                partyId = partyId,
                userId = stompSessionUserRegistry.resolveUserId(headerAccessor.sessionAttributes),
                participantToken = request.participantToken,
            )
        // REST 의 204 응답에 해당하는 개인 완료 신호.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "fireworks-triggered", payload)
    }
}
