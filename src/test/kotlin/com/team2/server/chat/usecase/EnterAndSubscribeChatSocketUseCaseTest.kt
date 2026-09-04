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
