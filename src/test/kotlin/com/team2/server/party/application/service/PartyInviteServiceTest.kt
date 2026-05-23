package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyInviteServiceTest {
    private val partyRepository: PartyRepository = mock()
    private val partyInviteRepository: PartyInviteRepository = mock()
    private val participantRepository: ParticipantRepository = mock()
    private val service = PartyInviteService(partyRepository, partyInviteRepository, participantRepository)

    private fun makeParty(
        id: Long = 1L,
        ownerId: Long = 1L,
        startedAt: LocalDateTime = LocalDateTime.now().plusDays(2),
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): RealtimeParty {
        val party = RealtimeParty(ownerId = ownerId, startedAt = startedAt)
        party.createdAt = createdAt
        var clazz: Class<*>? = party.javaClass
        while (clazz != null) {
            try {
                val idField = clazz.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(party, id)
                break
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        return party
    }

    private fun makeInvite(
        party: Party,
        token: String = "abc1def2ghi3jklm",
        expiresAt: LocalDateTime = LocalDateTime.now().plusHours(1),
    ): PartyInvite = PartyInvite(party = party, token = token, expiresAt = expiresAt)

    @Test
    fun `파티가 없으면 PARTY_NOT_FOUND 예외`() {
        whenever(partyRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<BusinessException> { service.activateInviteLink(99L, 1L) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `파티 멤버가 아니면 PARTY_FORBIDDEN 예외`() {
        val party = makeParty()
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 2L)).thenReturn(false)

        val ex = assertThrows<BusinessException> { service.activateInviteLink(1L, 2L) }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `파티 주인이면 참여자가 아니어도 초대링크 활성화 가능`() {
        val party = makeParty(ownerId = 1L)
        val existingInvite = makeInvite(party, token = "existingtoken1234")
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(existingInvite)

        val result = service.activateInviteLink(1L, 1L)

        verify(participantRepository, never()).existsByPartyIdAndUserId(any(), any())
        assertEquals(existingInvite.token, result)
    }

    @Test
    fun `유효한 초대링크가 이미 있으면 새로 만들지 않고 재사용`() {
        val party = makeParty(ownerId = 2L)
        val existingInvite = makeInvite(party, token = "existingtoken1234")
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 1L)).thenReturn(true)
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(existingInvite)

        val result = service.activateInviteLink(1L, 1L)

        verify(partyInviteRepository, never()).save(any())
        assertEquals(existingInvite.token, result)
    }

    @Test
    fun `유효한 링크가 없으면 새 토큰으로 생성`() {
        val party = makeParty()
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 1L)).thenReturn(true)
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(null)
        whenever(partyInviteRepository.save(any())).thenAnswer { it.arguments[0] as PartyInvite }

        val result = service.activateInviteLink(1L, 1L)

        verify(partyInviteRepository).save(any())
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `초대 링크 만료 시간은 파티 시작 후 7일`() {
        val createdAt = LocalDateTime.now().minusDays(1)
        val startedAt = LocalDateTime.now().plusDays(1)
        val party = makeParty(startedAt = startedAt, createdAt = createdAt)
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 1L)).thenReturn(true)
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(null)

        var savedInvite: PartyInvite? = null
        whenever(partyInviteRepository.save(any())).thenAnswer {
            (it.arguments[0] as PartyInvite).also { invite -> savedInvite = invite }
        }

        service.activateInviteLink(1L, 1L)

        assertEquals(startedAt.plusDays(Party.ENDED_AFTER_DAYS), savedInvite!!.expiresAt)
    }

    @Test
    fun `파티 시작 후 7일이 지나면 새 초대링크를 만들지 않는다`() {
        val startedAt =
            LocalDateTime
                .now()
                .minusDays(Party.ENDED_AFTER_DAYS)
                .minusSeconds(1)
        val party = makeParty(startedAt = startedAt)
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 1L)).thenReturn(true)
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(null)

        val ex = assertThrows<BusinessException> { service.activateInviteLink(1L, 1L) }

        assertEquals(ErrorCode.PARTY_ENDED, ex.errorCode)
        verify(partyInviteRepository, never()).save(any())
    }

    // --- resolveEnterableRealtimeInvite ---

    @Test
    fun `resolveEnterableRealtimeInvite 정상 진입 윈도우면 invite 반환`() {
        val realtimeParty = makeParty(startedAt = LocalDateTime.now().minusSeconds(1))
        val invite = makeInvite(party = realtimeParty, expiresAt = LocalDateTime.now().plusMinutes(30))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val result = service.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())

        assertEquals(invite, result)
    }

    @Test
    fun `resolveEnterableRealtimeInvite invite 없으면 PARTY_NOT_FOUND`() {
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(null)
        val e =
            assertThrows<BusinessException> {
                service.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
            }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `resolveEnterableRealtimeInvite 만료된 토큰이면 INVITE_LINK_EXPIRED`() {
        val realtimeParty = makeParty(startedAt = LocalDateTime.now())
        val invite = makeInvite(party = realtimeParty, expiresAt = LocalDateTime.now().minusMinutes(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        val e =
            assertThrows<BusinessException> {
                service.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
            }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, e.errorCode)
    }

    @Test
    fun `resolveEnterableRealtimeInvite PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val paperOnly =
            PaperOnlyParty(
                ownerId = 1L,
                celebrantNickname = "x",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        val invite =
            PartyInvite(
                party = paperOnly,
                token = "tok",
                expiresAt = LocalDateTime.now().plusDays(7),
            )
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        val e =
            assertThrows<BusinessException> {
                service.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
            }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, e.errorCode)
    }

    @Test
    fun `resolveEnterableRealtimeInvite 진입 윈도우 밖이면 CHAT_NOT_ACTIVE`() {
        val realtimeParty = makeParty(startedAt = LocalDateTime.now().plusHours(1))
        val invite = makeInvite(party = realtimeParty, expiresAt = LocalDateTime.now().plusDays(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val e =
            assertThrows<BusinessException> {
                service.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
            }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, e.errorCode)
    }

    @Test
    fun `findLatestUsableInviteToken 파티가 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.existsById(1L)).thenReturn(false)

        val e =
            assertThrows<BusinessException> {
                service.findLatestUsableInviteToken(1L, LocalDateTime.now())
            }

        assertEquals(ErrorCode.PARTY_NOT_FOUND, e.errorCode)
        verify(partyInviteRepository, never())
            .findFirstByPartyIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(any(), any())
    }

    @Test
    fun `findLatestUsableInviteToken 유효한 초대링크가 없으면 PARTY_INVITE_NOT_FOUND`() {
        whenever(partyRepository.existsById(1L)).thenReturn(true)
        whenever(partyInviteRepository.findFirstByPartyIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(any(), any()))
            .thenReturn(null)

        val e =
            assertThrows<BusinessException> {
                service.findLatestUsableInviteToken(1L, LocalDateTime.now())
            }

        assertEquals(ErrorCode.PARTY_INVITE_NOT_FOUND, e.errorCode)
    }
}
