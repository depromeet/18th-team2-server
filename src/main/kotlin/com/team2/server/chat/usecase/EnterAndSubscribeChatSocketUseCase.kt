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
    @Transactional
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
        clientRequestId: String,
    ) {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)
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
