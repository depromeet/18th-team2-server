package com.team2.server.chat.usecase

import com.team2.server.chat.application.dto.EnterRealtimePartyResult
import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

/**
 * 입장 ack 는 반드시 커밋 이후에 나가야 한다.
 *
 * 커밋 전에 participantToken 을 넘기면 클라이언트가 그 토큰으로 보낸 후속 요청이
 * 아직 커밋되지 않은 참가자를 조회하게 되어 CHARACTER_REQUIRED 등으로 실패한다.
 * 그래서 gateway 는 실제 구현을 쓰고, 그 바깥의 전송 채널만 mock 으로 관찰한다.
 */
class EnterAndSubscribeChatSocketUseCaseTest {
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase = mock()
    private val chatHistorySnapshotResolver: ChatHistorySnapshotResolver = mock()
    private val messagingTemplate: SimpMessagingTemplate = mock()
    private val applicationEventPublisher: ApplicationEventPublisher = mock()

    private val useCase =
        EnterAndSubscribeChatSocketUseCase(
            enterRealtimePartyUseCase,
            chatHistorySnapshotResolver,
            ChatSocketGateway(messagingTemplate, applicationEventPublisher),
        )

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)

    @AfterEach
    fun tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `커밋 전에는 개인 ack 프레임을 하나도 내보내지 않는다`() {
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult())
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(ChatHistorySnapshotResolver.Snapshot(messages = emptyList(), enteringCharacterImageUrl = null))
        TransactionSynchronizationManager.initSynchronization()

        useCase.enterAndSubscribe("tok", null, request, clientRequestId = "req-1")

        verifyNoInteractions(messagingTemplate)
    }

    private fun enterResult(): EnterRealtimePartyResult =
        EnterRealtimePartyResult(
            participantToken = "abc12345",
            partyId = 1L,
            startedAt = now.minusMinutes(5),
            isCelebrant = false,
            nickname = "토끼왕",
            characterId = 1L,
            partyState =
                RealtimePartyStateResult(
                    partyId = 1L,
                    status = RealtimePartyStatus.LIVE_OPEN,
                    liveStartAt = now.minusMinutes(5),
                    endingStartedAt = null,
                    endedAt = now.plusMinutes(5),
                    endingReason = null,
                    hostNickname = "주최자",
                    hostFarewellAvailable = true,
                    hostFarewellAvailableAt = now.minusMinutes(1),
                    serverNow = now,
                ),
        )
}
