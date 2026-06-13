package com.team2.server.chat.usecase

import com.team2.server.chat.application.port.RealtimePartyEntryProfilePort
import com.team2.server.chat.application.port.RealtimePartyEntryProfileResult
import com.team2.server.chat.application.support.RealtimePartyEntryProfileResolver
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.usecase.MarkRealtimePartyHostEnteredUseCase
import com.team2.server.party.application.usecase.ResolveRealtimePartyEndingInfoUseCase
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterRealtimePartyUseCaseTest {
    @Mock lateinit var partyInviteService: PartyInviteService

    @Mock lateinit var entryProfilePort: RealtimePartyEntryProfilePort

    @Mock lateinit var markRealtimePartyHostEnteredUseCase: MarkRealtimePartyHostEnteredUseCase

    @Mock lateinit var resolveRealtimePartyEndingInfoUseCase: ResolveRealtimePartyEndingInfoUseCase

    lateinit var useCase: EnterRealtimePartyUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    @BeforeEach
    fun setUp() {
        useCase =
            EnterRealtimePartyUseCase(
                partyInviteService = partyInviteService,
                profileResolver = RealtimePartyEntryProfileResolver(entryProfilePort),
                markRealtimePartyHostEnteredUseCase = markRealtimePartyHostEnteredUseCase,
                resolveRealtimePartyEndingInfoUseCase = resolveRealtimePartyEndingInfoUseCase,
                clock = clock,
            )
        Mockito
            .lenient()
            .`when`(resolveRealtimePartyEndingInfoUseCase(any(), any()))
            .thenReturn(RealtimePartyEndingInfo(null, "주최자"))
    }

    @Test
    fun `존재하지 않는 초대 토큰이면 PARTY_NOT_FOUND`() {
        whenever(partyInviteService.findUsableInvite(any(), any()))
            .thenThrow(BusinessException(ErrorCode.PARTY_NOT_FOUND))

        val ex = assertThrows<BusinessException> { useCase.enter("invalid", userId = null, request) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = now)
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `입장 가능 시간 이전이면 CHAT_NOT_ACTIVE`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `만료된 초대링크면 INVITE_LINK_EXPIRED`() {
        whenever(partyInviteService.findUsableInvite(any(), any()))
            .thenThrow(BusinessException(ErrorCode.INVITE_LINK_EXPIRED))

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 캐릭터면 CHARACTER_NOT_FOUND`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = null, request))
            .thenThrow(BusinessException(ErrorCode.CHARACTER_NOT_FOUND))

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `비로그인 사용자 첫 입장 - 익명 Participant + Profile 생성 후 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = null, request)).thenReturn(entryResult())

        val result = useCase.enter("tok", userId = null, request)

        assertNotNull(result.participantToken)
    }

    @Test
    fun `주최자 첫 채팅 입장은 주최자 입장 시각을 기록한다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = 1L, request)).thenReturn(entryResult(isCelebrant = true))
        whenever(markRealtimePartyHostEnteredUseCase(party.id, now)).thenReturn(now)

        useCase.enter("tok", userId = 1L, request)

        verify(markRealtimePartyHostEnteredUseCase).invoke(party.id, now)
        assertEquals(now, party.hostEnteredAt)
    }

    @Test
    fun `주최자가 아니면 입장해도 주최자 입장 시각을 기록하지 않는다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = null, request)).thenReturn(entryResult(isCelebrant = false))

        useCase.enter("tok", userId = null, request)

        verify(markRealtimePartyHostEnteredUseCase, never()).invoke(any(), any())
        assertEquals(null, party.hostEnteredAt)
    }

    @Test
    fun `주최자 입장 시각이 이미 저장되어 있으면 기존 값을 유지한다`() {
        val existingHostEnteredAt = now.minusSeconds(10)
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1), hostEnteredAt = existingHostEnteredAt)
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = 1L, request)).thenReturn(entryResult(isCelebrant = true))
        whenever(markRealtimePartyHostEnteredUseCase(party.id, now)).thenReturn(null)

        useCase.enter("tok", userId = 1L, request)

        verify(markRealtimePartyHostEnteredUseCase).invoke(party.id, now)
        assertEquals(existingHostEnteredAt, party.hostEnteredAt)
    }

    @Test
    fun `이미 프로필이 있는 사용자 재입장 - 기존 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = null, request))
            .thenReturn(entryResult(participantToken = "existing-uuid"))

        val result = useCase.enter("tok", userId = null, request)

        assertEquals("existing-uuid", result.participantToken)
    }

    @Test
    fun `회원도 participantToken이 있으면 LIVE_ENDING에서 재입장할 수 있다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(10).minusSeconds(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = "existing-uuid",
            )

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = 99L, reenterRequest))
            .thenReturn(entryResult(participantToken = "existing-uuid", nickname = "새닉네임"))

        val result = useCase.enter("tok", userId = 99L, reenterRequest)

        assertEquals("existing-uuid", result.participantToken)
        assertEquals("새닉네임", result.nickname)
    }

    @Test
    fun `주최자는 participantToken 재입장으로 닉네임을 변경할 수 없다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = "existing-uuid",
            )

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(resolveEntry(party, userId = 1L, reenterRequest))
            .thenThrow(BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE))

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = 1L, reenterRequest) }

        assertEquals(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE, ex.errorCode)
    }

    private fun resolveEntry(
        party: RealtimeParty,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): RealtimePartyEntryProfileResult =
        entryProfilePort.resolve(
            party = eq(party),
            userId = eq(userId),
            request = eq(request),
            now = eq(now),
        )

    private fun entryResult(
        participantToken: String = "participant-token",
        isCelebrant: Boolean = false,
        nickname: String = "토끼왕",
        characterId: Long? = 1L,
    ): RealtimePartyEntryProfileResult =
        RealtimePartyEntryProfileResult(
            participantToken = participantToken,
            isCelebrant = isCelebrant,
            nickname = nickname,
            characterId = characterId,
        )
}
