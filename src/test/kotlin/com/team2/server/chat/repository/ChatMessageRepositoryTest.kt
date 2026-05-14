package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@Import(TestcontainersConfiguration::class)
class ChatMessageRepositoryTest
    @Autowired
    constructor(
        private val chatMessageRepository: ChatMessageRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `countByPartyId - 해당 파티의 메시지 수 반환`() {
            val (party, profile) = saveRealtimeContext("counter")
            repeat(3) { idx ->
                chatMessageRepository.save(
                    ChatMessage(content = "m$idx", party = party, profile = profile),
                )
            }

            val count = chatMessageRepository.countByPartyId(party.id)

            assertEquals(3L, count)
        }

        @Test
        fun `findRecentByPartyId - createdAt DESC, id DESC 로 최근 N개 반환`() {
            val (party, profile) = saveRealtimeContext("recent")
            (1..5).forEach { idx ->
                chatMessageRepository.save(
                    ChatMessage(content = "m$idx", party = party, profile = profile),
                )
            }

            val recent = chatMessageRepository.findRecentByPartyId(party.id, PageRequest.of(0, 3))

            assertEquals(3, recent.size)
            assertEquals("m5", recent[0].content)
            assertEquals("m4", recent[1].content)
            assertEquals("m3", recent[2].content)
            assertEquals("nick-recent", recent[0].profile.nickname)
        }

        private fun saveRealtimeContext(token: String): Pair<RealtimeParty, RealtimeParticipantProfile> {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "p",
                        celebrantNickname = "홍",
                        startedAt = LocalDateTime.now(),
                    ),
                )
            val user =
                userRepository.save(
                    User(
                        name = "닉",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "chat-$token",
                        email = "$token@x",
                    ),
                )
            val participant = participantRepository.save(Participant(party = party, user = user))
            val profile =
                profileRepository.save(
                    RealtimeParticipantProfile(participant = participant, nickname = "nick-$token"),
                )
            return party to profile
        }
    }
