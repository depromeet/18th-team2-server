package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Chat", description = "실시간 채팅 API")
interface ChatApi {
    @Operation(summary = "실시간 파티 입장 (비로그인 포함)")
    @SwaggerApiResponse(responseCode = "201", description = "입장 성공, participantToken 반환")
    @InternalServerErrorResponse
    fun enterRealtimeParty(
        @Parameter(description = "초대 토큰") inviteToken: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
        request: EnterRealtimePartyRequest,
    ): ApiResponse<EnterRealtimePartyResponse>

    @Operation(summary = "채팅 메시지 전송")
    @SwaggerApiResponse(responseCode = "201", description = "메시지 전송 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun sendMessage(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰") participantToken: String?,
        request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse>

    @Operation(summary = "채팅 메시지 SSE 구독")
    @SwaggerApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun subscribe(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰") participantToken: String?,
    ): SseEmitter
}
