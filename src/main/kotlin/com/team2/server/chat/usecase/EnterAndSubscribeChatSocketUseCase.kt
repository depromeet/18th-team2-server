package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.UserEnteredEventPayload
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EnterAndSubscribeChatSocketUseCase(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val chatHistorySnapshotResolver: ChatHistorySnapshotResolver,
    private val chatSocketGateway: ChatSocketGateway,
) {
    /**
     * @param onEntered 입장 검증을 통과한 직후(개인 ack 전송 이전) 파티 id 와 함께 호출된다.
     *  STOMP 컨트롤러가 세션 단위 구독 인가를 등록하는 훅이다. 개인 ack 를 먼저 보내면
     *  클라이언트가 ack 를 받고 보낸 브로드캐스트 SUBSCRIBE 가 인가 등록보다 앞설 수 있어
     *  정상 입장인데도 구독이 거부되는 경합이 생긴다.
     */
    @Transactional
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        clientRequestId: String,
        onEntered: (Long) -> Unit = {},
    ) {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)
        onEntered(enterResult.partyId)

        val snapshot = chatHistorySnapshotResolver.resolve(enterResult.partyId, enterResult.characterId)

        chatSocketGateway.sendPersonal(enterResult.partyId, clientRequestId, "party-state", enterResult.partyState)
        chatSocketGateway.sendPersonal(
            enterResult.partyId,
            clientRequestId,
            "entered",
            EnterRealtimePartyResponse(enterResult.participantToken, snapshot.messages),
        )

        val enteredEventPayload =
            UserEnteredEventPayload(
                nickname = enterResult.nickname,
                characterId = enterResult.characterId,
                characterImageUrl = snapshot.enteringCharacterImageUrl,
                role = if (enterResult.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )
        chatSocketGateway.broadcastAfterCommit(enterResult.partyId, "user-entered", enteredEventPayload)
    }
}
