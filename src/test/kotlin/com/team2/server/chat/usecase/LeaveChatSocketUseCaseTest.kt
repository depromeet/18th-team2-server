package com.team2.server.chat.usecase

import com.team2.server.chat.application.support.ChatLeaveExecutor
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.UserLeftEventPayload
import com.team2.server.chat.infrastructure.websocket.ChatSocketGateway
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyUseCase
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class LeaveChatSocketUseCaseTest {
    @Mock lateinit var resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase

    @Mock lateinit var resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase

    @Mock lateinit var chatLeaveExecutor: ChatLeaveExecutor

    @Mock lateinit var chatSocketGateway: ChatSocketGateway

    @InjectMocks
    lateinit var useCase: LeaveChatSocketUseCase

    private val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
    private val profile =
        RealtimeParticipantProfile(
            participant = Participant(party = party, isCelebrant = false),
            nickname = "손님",
            participantToken = "tok",
        )

    @Test
    fun `구독 인가 회수는 user-left 브로드캐스트 이전에 일어난다`() {
        // 브로드캐스트가 먼저 나가면 그 프레임을 본 클라이언트의 SUBSCRIBE 가 인가 회수보다 앞설 수 있다.
        stubResolution()
        val order = mutableListOf<String>()
        whenever(chatSocketGateway.broadcastAfterCommit(party.id, "user-left", payload())).then {
            order.add("broadcast")
            null
        }

        useCase.leave(party.id, "tok") { order.add("onLeft") }

        assertEquals(listOf("onLeft", "broadcast"), order)
    }

    @Test
    fun `퇴장하면 user-left 페이로드를 반환한다`() {
        stubResolution()

        val result = useCase.leave(party.id, "tok")

        assertEquals("손님", result.nickname)
        assertEquals(ParticipantRole.PARTICIPANT, result.role)
        verify(chatSocketGateway).broadcastAfterCommit(party.id, "user-left", payload())
    }

    @Test
    fun `퇴장에 실패하면 인가 회수도 브로드캐스트도 하지 않는다`() {
        whenever(resolveRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, null, "tok"))
            .thenThrow(BusinessException(ErrorCode.PARTY_FORBIDDEN))
        var revoked = false

        val ex = assertThrows<BusinessException> { useCase.leave(party.id, "tok") { revoked = true } }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
        assertTrue(!revoked)
        verify(chatSocketGateway, never()).broadcastAfterCommit(party.id, "user-left", payload())
    }

    private fun stubResolution() {
        whenever(resolveRealtimePartyUseCase.invoke(party.id)).thenReturn(party)
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(party.id, null, "tok")).thenReturn(profile)
        whenever(chatLeaveExecutor.execute(party, profile, null)).thenReturn(payload())
    }

    private fun payload() = UserLeftEventPayload("손님", ParticipantRole.PARTICIPANT)
}
