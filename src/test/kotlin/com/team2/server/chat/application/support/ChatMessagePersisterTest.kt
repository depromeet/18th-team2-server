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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class ChatMessagePersisterTest {
    @Mock lateinit var chatMessageRepository: ChatMessageRepository

    @Mock lateinit var imageUrlReader: ImageUrlReader

    @InjectMocks
    lateinit var persister: ChatMessagePersister

    private val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))

    @Test
    fun `캐릭터가 있으면 썸네일 URL을 채워 응답을 만든다`() {
        val participant = Participant(party = party, isCelebrant = true)
        val character = Character(name = "토끼")
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)
        whenever(
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                listOf(character.id),
                1,
            ),
        ).thenReturn(mapOf(character.id to "https://example.com/rabbit.png"))

        val response = persister.persist(party, profile, "안녕하세요!")

        assertEquals("안녕하세요!", response.content)
        assertEquals("토끼왕", response.senderNickname)
        assertEquals(ParticipantRole.CELEBRANT, response.senderRole)
        assertEquals("https://example.com/rabbit.png", response.senderCharacterImageUrl)
    }

    @Test
    fun `캐릭터가 없으면 썸네일을 조회하지 않고 null로 둔다`() {
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님", participantToken = "tok")
        val savedMessage = ChatMessage(content = "안녕", party = party, profile = profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = persister.persist(party, profile, "안녕")

        assertEquals("손님", response.senderNickname)
        assertEquals(ParticipantRole.PARTICIPANT, response.senderRole)
        assertNull(response.senderCharacterImageUrl)
        verify(imageUrlReader, never()).findImageUrlByTargetIdsAndSortOrder(any(), any(), any())
    }

    @Test
    fun `전달받은 파티와 프로필로 메시지를 저장한다`() {
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님")
        val savedMessage = ChatMessage(content = "저장확인", party = party, profile = profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        persister.persist(party, profile, "저장확인")

        val captor = argumentCaptor<ChatMessage>()
        verify(chatMessageRepository).save(captor.capture())
        assertEquals("저장확인", captor.firstValue.content)
        assertEquals(party, captor.firstValue.party)
        assertEquals(profile, captor.firstValue.profile)
    }
}
