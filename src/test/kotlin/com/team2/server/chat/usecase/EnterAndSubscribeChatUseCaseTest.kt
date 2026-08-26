package com.team2.server.chat.usecase

import com.team2.server.chat.application.dto.EnterRealtimePartyResult
import com.team2.server.chat.application.support.ChatHistorySnapshotResolver
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterAndSubscribeChatUseCaseTest {
    @Mock lateinit var enterRealtimePartyUseCase: EnterRealtimePartyUseCase

    @Mock lateinit var chatHistorySnapshotResolver: ChatHistorySnapshotResolver

    @Mock lateinit var chatSseGateway: ChatSseGateway

    @InjectMocks
    lateinit var useCase: EnterAndSubscribeChatUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)

    private fun enterResult(
        partyId: Long = 1L,
        isCelebrant: Boolean = false,
    ): EnterRealtimePartyResult =
        EnterRealtimePartyResult(
            participantToken = "abc12345",
            partyId = partyId,
            startedAt = now.minusMinutes(5),
            isCelebrant = isCelebrant,
            nickname = "토끼왕",
            characterId = 1L,
            partyState =
                RealtimePartyStateResult(
                    partyId = partyId,
                    status = RealtimePartyStatus.LIVE_OPEN,
                    liveStartAt = now.minusMinutes(5),
                    endingStartedAt = null,
                    endedAt = now.plusMinutes(5).plusSeconds(60),
                    endingReason = null,
                    hostNickname = "주최자",
                    hostFarewellAvailable = true,
                    hostFarewellAvailableAt = now.minusMinutes(1),
                    serverNow = now,
                ),
        )

    @Test
    fun `입장 성공 - 히스토리 없어도 entered 이벤트와 함께 emitter 반환`() {
        val enterResult = enterResult()
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(ChatHistorySnapshotResolver.Snapshot(messages = emptyList(), enteringCharacterImageUrl = null))

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
    }

    @Test
    fun `히스토리 존재하면 스냅샷을 조회해 메시지를 담은 emitter 반환`() {
        val enterResult = enterResult()
        val snapshotMessages =
            listOf(
                mock<ChatMessageResponse>(),
                mock<ChatMessageResponse>(),
            )
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(
                ChatHistorySnapshotResolver.Snapshot(
                    messages = snapshotMessages,
                    enteringCharacterImageUrl = null,
                ),
            )

        val emitter = useCase.enterAndSubscribe("tok", null, request)

        assertNotNull(emitter)
        verify(chatHistorySnapshotResolver).resolve(1L, 1L)
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
    }

    @Test
    fun `입장 성공 - user-entered 이벤트 브로드캐스트`() {
        val enterResult = enterResult(isCelebrant = true)
        whenever(enterRealtimePartyUseCase.enter("tok", null, request)).thenReturn(enterResult)
        whenever(chatHistorySnapshotResolver.resolve(1L, 1L))
            .thenReturn(
                ChatHistorySnapshotResolver.Snapshot(
                    messages = emptyList(),
                    enteringCharacterImageUrl = "https://example.com/rabbit.png",
                ),
            )

        useCase.enterAndSubscribe("tok", null, request)

        verify(chatSseGateway).broadcastAfterCommit(eq(1L), any(), eq("abc12345"))
        verify(chatSseGateway).subscribe(eq(1L), any(), eq("abc12345"))
    }
}
