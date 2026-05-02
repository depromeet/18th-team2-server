package com.team2.server.party.repository

import com.team2.server.party.entity.Guest
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
class ParticipantRepositoryTest
    @Autowired
    constructor(
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val guestRepository: GuestRepository,
        private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
        private val userRepository: UserRepository,
        private val entityManager: EntityManager,
    ) {
        private lateinit var party: Party
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
                    user = user,
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
                    user = user,
                ),
            )

            assertTrue(participantRepository.existsByPartyAndUser(party, user))
        }

        @Test
        fun `비회원 참여자는 guest로 저장된다`() {
            val guest =
                guestRepository.save(
                    Guest(
                        tokenHash = "guest-token-hash",
                        lastSeenAt = LocalDateTime.now(),
                    ),
                )

            val participant =
                participantRepository.save(
                    Participant(
                        party = party,
                        guest = guest,
                    ),
                )

            assertEquals(guest, participant.guest)
            assertNull(participant.user)
        }

        @Test
        fun `롤링페이퍼 작성자 닉네임은 실시간 프로필 변경과 무관하게 스냅샷으로 유지된다`() {
            val participant =
                participantRepository.save(
                    Participant(
                        party = party,
                        user = user,
                    ),
                )
            val profile =
                realtimeParticipantProfileRepository.save(
                    RealtimeParticipantProfile(
                        participant = participant,
                        nickname = "작성당시닉네임",
                    ),
                )
            val wrapper = RollingPaperWrapper(name = "기본테마")
            entityManager.persist(wrapper)
            val rollingPaper =
                RollingPaper(
                    theme = wrapper,
                    writer = participant,
                    party = party,
                    writerNickname = profile.nickname,
                    content = "축하해요",
                )
            entityManager.persist(rollingPaper)

            profile.nickname = "변경된닉네임"
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(RollingPaper::class.java, rollingPaper.id)

            assertEquals("작성당시닉네임", found.writerNickname)
        }
    }
