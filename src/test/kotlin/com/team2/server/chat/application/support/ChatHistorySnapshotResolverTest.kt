package com.team2.server.chat.application.support

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ChatHistorySnapshotResolverTest {
    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var imageUrlReader: ImageUrlReader

    @InjectMocks
    lateinit var resolver: ChatHistorySnapshotResolver

    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)

    @Test
    fun `메시지 존재 - 히스토리와 입장 캐릭터 썸네일을 포함한 스냅샷 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = true)
        val character = Character(name = "토끼")
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)
        val message = ChatMessage(content = "안녕하세요", party = party, profile = profile)
        val enteringCharacterId = 999L

        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(listOf(message))
        whenever(
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                listOf(character.id, enteringCharacterId),
                1,
            ),
        ).thenReturn(
            mapOf(
                character.id to "https://example.com/rabbit.png",
                enteringCharacterId to "https://example.com/fox.png",
            ),
        )

        val snapshot = resolver.resolve(1L, enteringCharacterId)

        assertEquals(1, snapshot.messages.size)
        val responseMessage = snapshot.messages[0]
        assertEquals("안녕하세요", responseMessage.content)
        assertEquals("토끼왕", responseMessage.senderNickname)
        assertEquals(ParticipantRole.CELEBRANT, responseMessage.senderRole)
        assertEquals("https://example.com/rabbit.png", responseMessage.senderCharacterImageUrl)
        assertEquals("https://example.com/fox.png", snapshot.enteringCharacterImageUrl)
    }

    @Test
    fun `히스토리 없음 - 빈 메시지 목록과 입장 캐릭터 썸네일만 반환`() {
        val enteringCharacterId = 5L
        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(emptyList())
        whenever(
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                listOf(enteringCharacterId),
                1,
            ),
        ).thenReturn(mapOf(enteringCharacterId to "https://example.com/rabbit.png"))

        val snapshot = resolver.resolve(1L, enteringCharacterId)

        assertTrue(snapshot.messages.isEmpty())
        assertEquals("https://example.com/rabbit.png", snapshot.enteringCharacterImageUrl)
    }

    @Test
    fun `입장 캐릭터 없음 - enteringCharacterImageUrl null이고 예외 없이 처리`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(5))
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님")
        val message = ChatMessage(content = "안녕하세요", party = party, profile = profile)

        whenever(chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(1L))
            .thenReturn(listOf(message))
        whenever(
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                emptyList(),
                1,
            ),
        ).thenReturn(emptyMap())

        val snapshot = resolver.resolve(1L, null)

        assertEquals(1, snapshot.messages.size)
        assertNull(snapshot.messages[0].senderCharacterImageUrl)
        assertEquals(ParticipantRole.PARTICIPANT, snapshot.messages[0].senderRole)
        assertNull(snapshot.enteringCharacterImageUrl)
    }
}
