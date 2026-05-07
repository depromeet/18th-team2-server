package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class EnterAndSubscribeChatUseCase(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val chatMessageRepository: ChatMessageRepository,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): SseEmitter {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)

        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)
        sseEmitterRegistry.subscribe(enterResult.partyId, emitter)

        val messages =
            chatMessageRepository
                .findAllByPartyIdWithProfileOrderByCreatedAtAsc(enterResult.partyId)
                .map { ChatMessageResponse.from(it) }

        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("entered")
                    .data(
                        EnterRealtimePartyResponse(
                            participantToken = enterResult.participantToken,
                            messages = messages,
                        ),
                    ).build(),
            )
        } catch (e: IllegalStateException) {
            emitter.completeWithError(e)
        } catch (e: java.io.IOException) {
            emitter.completeWithError(e)
        }

        return emitter
    }

    companion object {
        private const val EMITTER_TIMEOUT_MS = 15 * 60 * 1000L
    }
}
