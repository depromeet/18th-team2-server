package com.team2.server.burstgame.api

import com.team2.server.burstgame.api.dto.CandleBlowSocketRequest
import com.team2.server.burstgame.application.usecase.BlowCandleUseCase
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.chat.infrastructure.websocket.StompSessionUserRegistry
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class CandleBlowSocketController(
    private val blowCandleUseCase: BlowCandleUseCase,
    private val stompSessionUserRegistry: StompSessionUserRegistry,
    private val chatSocketGateway: ChatSocketGateway,
) {
    @MessageMapping("/parties/{partyId}/candle-blow/candles/{candleId}")
    fun blowCandle(
        @DestinationVariable partyId: Long,
        @DestinationVariable candleId: Int,
        @Valid @Payload request: CandleBlowSocketRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val response =
            blowCandleUseCase(
                partyId = partyId,
                candleId = candleId,
                userId = stompSessionUserRegistry.resolveUserId(headerAccessor.sessionAttributes),
                participantToken = request.participantToken,
            )
        // REST 의 200 응답에 해당하는 개인 완료 신호.
        chatSocketGateway.sendPersonal(partyId, request.clientRequestId, "candle-blown", response)
    }
}
