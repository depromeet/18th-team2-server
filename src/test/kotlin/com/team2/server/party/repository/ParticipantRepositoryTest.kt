package com.team2.server.party.repository

import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
class ParticipantRepositoryTest
    @Autowired
    constructor(
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
    ) {
        private lateinit var party: Party
        private lateinit var character: Character
        private lateinit var user: User

        @BeforeEach
        fun setUp() {
            party =
                partyRepository.save(
                    Party(
                        ownerId = 1L,
                        name = "테스트파티",
                        celebrantNickname = "홍길동",
                        purpose = PartyPurpose.BIRTHDAY,
                        option = PartyOption.REALTIME,
                        startedAt = LocalDateTime.now(),
                        endedAt = LocalDateTime.now().plusDays(7),
                        isChattingAllow = true,
                    ),
                )
            character = characterRepository.save(Character(name = "곰돌이"))
            user =
                userRepository.save(
                    User(
                        name = "유저",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "kakao-test",
                        email = "test@kakao.local",
                    ),
                )
        }

        @Test
        fun `findByPartyAndUser 매칭 참여자 반환`() {
            participantRepository.save(
                Participant(
                    party = party,
                    character = character,
                    user = user,
                    nickname = "닉네임",
                ),
            )

            val found = participantRepository.findByPartyAndUser(party, user)

            assertNotNull(found)
        }

        @Test
        fun `findByPartyAndUser 미매칭 시 null`() {
            val found = participantRepository.findByPartyAndUser(party, user)
            assertNull(found)
        }

        @Test
        fun `existsByPartyAndUser true 반환`() {
            participantRepository.save(
                Participant(
                    party = party,
                    character = character,
                    user = user,
                    nickname = "닉네임",
                ),
            )

            assertTrue(participantRepository.existsByPartyAndUser(party, user))
        }
    }
