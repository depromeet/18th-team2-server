package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.reflect.Field
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
        startedAt: LocalDateTime? = LocalDateTime.now().plusDays(2),
        endedAt: LocalDateTime? = LocalDateTime.now().plusDays(2).plusHours(3),
        option: PartyOption = PartyOption.REALTIME,
    ): Party {
        val party = Party(ownerId = ownerId, startedAt = startedAt, endedAt = endedAt, option = option)
        val idField: Field = party.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(party, id)
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
        assertEquals(existingInvite.token, result.token)
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
        assertEquals(existingInvite.token, result.token)
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
        assertTrue(result.token.isNotBlank())
    }

    @Test
    fun `링크 만료 시간은 파티 종료 시간까지`() {
        val endedAt = LocalDateTime.of(2024, 11, 26, 22, 0)
        val party = makeParty(endedAt = endedAt)
        whenever(partyRepository.findById(1L)).thenReturn(Optional.of(party))
        whenever(participantRepository.existsByPartyIdAndUserId(1L, 1L)).thenReturn(true)
        whenever(partyInviteRepository.findByPartyIdAndExpiresAtAfter(any(), any())).thenReturn(null)

        var savedInvite: PartyInvite? = null
        whenever(partyInviteRepository.save(any())).thenAnswer {
            (it.arguments[0] as PartyInvite).also { invite -> savedInvite = invite }
        }

        service.activateInviteLink(1L, 1L)

        assertEquals(endedAt, savedInvite!!.expiresAt)
    }
}
