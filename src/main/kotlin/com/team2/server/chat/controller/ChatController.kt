package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.usecase.EnterAndSubscribeChatUseCase
import com.team2.server.chat.usecase.LeaveChatUseCase
import com.team2.server.chat.usecase.SendChatMessageUseCase
import com.team2.server.common.web.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class ChatController(
    private val enterAndSubscribeChatUseCase: EnterAndSubscribeChatUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val leaveChatUseCase: LeaveChatUseCase,
) : ChatApi {
    @PostMapping(
        "/api/v1/party-invites/{inviteToken}/realtime-participants/stream",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    override fun enterAndSubscribe(
        @PathVariable inviteToken: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestBody @Valid request: EnterRealtimePartyRequest,
    ): SseEmitter = enterAndSubscribeChatUseCase.enterAndSubscribe(inviteToken, principal?.userId, request)

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/parties/{partyId}/chat-messages")
    override fun sendMessage(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @RequestBody @Valid request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse> =
        ApiResponse.success(
            HttpStatus.CREATED,
            sendChatMessageUseCase.send(partyId, principal?.userId, participantToken, request),
        )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/v1/parties/{partyId}/realtime-participants")
    override fun leaveParty(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ) {
        leaveChatUseCase.leave(partyId, principal?.userId, participantToken)
    }
}
