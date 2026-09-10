package com.team2.server.party.application.service

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyPurpose
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class PartyServiceTest {
    @Mock
    lateinit var partyRepository: PartyRepository

    @Mock
    lateinit var participantRepository: ParticipantRepository

    @Mock
    lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository

    @Mock
    lateinit var partyInviteRepository: PartyInviteRepository

    @Mock
    lateinit var chatMessageRepository: ChatMessageRepository

    @Mock
    lateinit var rollingPaperRepository: RollingPaperRepository

    @InjectMocks
    lateinit var partyService: PartyService

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val idField = clazz.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(entity, id)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException("id not found in class hierarchy of ${entity.javaClass.name}")
    }

    private fun newParty(id: Long = 1L): RealtimeParty {
        val party =
            RealtimeParty(
                ownerId = 1L,
                name = "생일파티",
                celebrantNickname = "홍길동",
                purpose = PartyPurpose.BIRTHDAY,
                startedAt = LocalDateTime.now(),
            )
        setId(party, id)
        return party
    }

    @Test
    fun `deleteParty 파티가 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(99L)).thenReturn(null)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 99L, userId = 1L)
            }

        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 주최자가 아니면 PARTY_FORBIDDEN`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 10L)
        whenever(partyRepository.findPartyById(10L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 10L, userId = 999L)
            }

        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 파티가 이미 시작됐으면 PARTY_ALREADY_STARTED`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().minusMinutes(1),
            )
        setId(party, 10L)
        whenever(partyRepository.findPartyById(10L)).thenReturn(party)

        val ex =
            assertThrows<BusinessException> {
                partyService.deleteParty(partyId = 10L, userId = 1L)
            }

        assertEquals(ErrorCode.PARTY_ALREADY_STARTED, ex.errorCode)
        verify(partyRepository, never()).delete(any<Party>())
    }

    @Test
    fun `deleteParty 정상 삭제 시 연관 데이터를 순서대로 삭제한다`() {
        val party =
            RealtimeParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 10L)

        val participant1 = Participant(party = party, user = null, isCelebrant = true)
        setId(participant1, 100L)
        val participant2 = Participant(party = party, user = null, isCelebrant = false)
        setId(participant2, 101L)

        whenever(partyRepository.findPartyById(10L)).thenReturn(party)
        whenever(participantRepository.findAllByPartyId(10L))
            .thenReturn(listOf(participant1, participant2))

        partyService.deleteParty(partyId = 10L, userId = 1L)

        val order =
            inOrder(
                chatMessageRepository,
                rollingPaperRepository,
                realtimeParticipantProfileRepository,
                participantRepository,
                partyInviteRepository,
                partyRepository,
            )
        order.verify(chatMessageRepository).deleteAllByPartyId(10L)
        order.verify(rollingPaperRepository).deleteAllByPartyId(10L)
        order
            .verify(realtimeParticipantProfileRepository)
            .deleteAllByParticipantIdIn(listOf(100L, 101L))
        order.verify(participantRepository).deleteAll(listOf(participant1, participant2))
        order.verify(partyInviteRepository).deleteAllByPartyId(10L)
        order.verify(partyRepository).delete(party)
    }

    @Test
    fun `deleteParty PAPER_ONLY 파티 삭제 시 realtimeParticipantProfile을 삭제하지 않는다`() {
        val party =
            PaperOnlyParty(
                ownerId = 1L,
                celebrantNickname = "홍길동",
                startedAt = LocalDateTime.now().plusDays(1),
            )
        setId(party, 20L)

        whenever(partyRepository.findPartyById(20L)).thenReturn(party)
        whenever(participantRepository.findAllByPartyId(20L)).thenReturn(emptyList())

        partyService.deleteParty(partyId = 20L, userId = 1L)

        verify(realtimeParticipantProfileRepository, never()).deleteAllByParticipantIdIn(any())
    }
}
