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
     * 대신 STOMP 세션의 파티 입장 기록을 지워 구독 인가를 회수한다([onLeft]).
     *
     * @param onLeft 퇴장 처리를 마친 직후(브로드캐스트 이전) 파티 id 와 함께 호출된다.
     *  STOMP 컨트롤러가 세션 단위 구독 인가를 회수하는 훅으로, 입장 경로의 `onEntered` 와 대칭이다.
     *  브로드캐스트 이후에 회수하면 user-left 프레임이 이미 나간 뒤라 그 사이에 도착한 SUBSCRIBE 가
     *  통과한다. 커밋 이전에 회수하므로 트랜잭션이 롤백되면 인가만 잃고(fail-closed) 열리지는 않는다.
     */
    @Transactional
    fun leave(
        partyId: Long,
        participantToken: String,
        onLeft: (Long) -> Unit = {},
    ): UserLeftEventPayload {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, null, participantToken)

        val payload = chatLeaveExecutor.execute(party, profile, null)
        onLeft(partyId)
        chatSocketGateway.broadcastAfterCommit(partyId, "user-left", payload)
        return payload
    }
}
