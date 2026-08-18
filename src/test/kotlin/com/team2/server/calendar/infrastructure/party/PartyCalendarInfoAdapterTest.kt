package com.team2.server.calendar.infrastructure.party

import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyPurpose
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartyCalendarInfoAdapterTest {
    private val partyService: PartyService = mock()
    private val participantService: ParticipantService = mock()
    private val partyInviteService: PartyInviteService = mock()
    private val adapter =
        PartyCalendarInfoAdapter(
            partyService = partyService,
            participantService = participantService,
            partyInviteService = partyInviteService,
            webBaseUrl = "https://example.com",
        )

    private val now = LocalDateTime.of(2026, 8, 18, 12, 0)
    private val startedAt = LocalDateTime.of(2026, 8, 20, 19, 0)

    @Test
    fun `호스트는 참여자 검증 없이 파티 정보를 얻는다`() {
        val party =
            PaperOnlyParty(
                ownerId = 10L,
                startedAt = startedAt,
                celebrantNickname = "지민",
                purpose = PartyPurpose.BIRTHDAY,
            )
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertEquals(CelebrationKind.BIRTHDAY, info.celebrationKind)
        assertEquals("지민", info.celebrantName)
        assertEquals(startedAt, info.startedAt)
        assertEquals("https://example.com/invite/token-1", info.inviteUrl)
    }

    @Test
    fun `참여 중인 멤버는 파티 정보를 얻는다`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt, celebrantNickname = "지민")
        val participant = Participant(party = party, hasLeft = false)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, 20L, null)).thenReturn(participant)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 20L, now = now)

        assertEquals(startedAt, info.startedAt)
    }

    @Test
    fun `파티를 나간 참여자는 PARTY_FORBIDDEN`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt)
        val participant = Participant(party = party, hasLeft = true)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(participantService.requireCallerParticipant(1L, 20L, null)).thenReturn(participant)

        val exception =
            kotlin.runCatching { adapter.loadForMember(partyId = 1L, userId = 20L, now = now) }.exceptionOrNull()

        assertEquals(ErrorCode.PARTY_FORBIDDEN, (exception as BusinessException).errorCode)
    }

    @Test
    fun `사용 가능한 초대 링크가 없으면 inviteUrl 은 null`() {
        val party = PaperOnlyParty(ownerId = 10L, startedAt = startedAt, celebrantNickname = "지민")
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any()))
            .thenThrow(BusinessException(ErrorCode.PARTY_INVITE_NOT_FOUND))

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertNull(info.inviteUrl)
    }

    @Test
    fun `파티 목적을 CelebrationKind 로 매핑한다`() {
        val party =
            PaperOnlyParty(ownerId = 10L, startedAt = startedAt, purpose = PartyPurpose.WEDDING)
        whenever(partyService.requireParty(1L)).thenReturn(party)
        whenever(partyInviteService.findLatestUsableInviteToken(any(), any())).thenReturn("token-1")

        val info = adapter.loadForMember(partyId = 1L, userId = 10L, now = now)

        assertEquals(CelebrationKind.WEDDING, info.celebrationKind)
    }
}
