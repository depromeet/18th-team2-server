package com.team2.server.burstgame.api

import com.team2.server.burstgame.api.dto.SubmitBurstGameTapSocketRequest
import com.team2.server.burstgame.application.usecase.SubmitBurstGameTapUseCase
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompSessionUserRegistry
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class BurstGameSocketController(
    private val submitBurstGameTapUseCase: SubmitBurstGameTapUseCase,
    private val stompSessionUserRegistry: StompSessionUserRegistry,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @MessageMapping("/parties/{partyId}/burst-game/taps")
    fun submitTaps(
        @DestinationVariable partyId: Long,
        @Valid @Payload request: SubmitBurstGameTapSocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val response =
            submitBurstGameTapUseCase(
                partyId = partyId,
                userId = stompSessionUserRegistry.resolveUserId(headerAccessor.sessionAttributes),
                participantToken = request.participantToken,
                tapCount = request.tapCount,
                clientSequence = request.clientSequence,
            )
        // REST 의 200 응답에 해당하는 개인 완료 신호.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "tap-submitted", response)
    }
}
