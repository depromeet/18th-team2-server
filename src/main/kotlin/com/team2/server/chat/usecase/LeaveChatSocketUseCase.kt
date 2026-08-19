package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatLeaveExecutor
import com.team2.server.chat.dto.UserLeftEventPayload
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeaveChatSocketUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val chatLeaveExecutor: ChatLeaveExecutor,
    private val chatSocketGateway: ChatSocketGateway,
) {
    /**
     * WebSocket 채널의 퇴장.
     *
     * SSE 와 달리 파티별 emitter 객체가 없으므로 해제할 구독 자원이 없다.
     * 대신 컨트롤러가 성공 이후 STOMP 세션의 파티 입장 기록을 지워 구독 인가를 회수한다.
     */
    @Transactional
    fun leave(
        partyId: Long,
        participantToken: String,
    ): UserLeftEventPayload {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, null, participantToken)

        val payload = chatLeaveExecutor.execute(party, profile, null)
        chatSocketGateway.broadcastAfterCommit(partyId, "user-left", payload)
        return payload
    }
}
