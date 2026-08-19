package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyPurpose
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.support.JpaSliceTestSupport
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealtimeParticipantProfileRepositoryTest
    @Autowired
    constructor(
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) : JpaSliceTestSupport() {
        @Test
        fun `findAllByPartyIdOrderByIdAsc - 특정 파티의 프로필 전체를 id ASC로 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "테스트",
                        celebrantNickname = "홍",
                        purpose = PartyPurpose.BIRTHDAY,
                        startedAt = LocalDateTime.now(),
                    ),
                )
            val user1 =
                userRepository.save(
                    User(
                        name = "해파리1",
                        birthDay = "01-01",
                        email = "a@a",
                        provider = AuthProvider.KAKAO,
                        providerId = "1",
                    ),
                )
            val user2 =
                userRepository.save(
                    User(
                        name = "해파리2",
                        birthDay = "02-02",
                        email = "b@b",
                        provider = AuthProvider.KAKAO,
                        providerId = "2",
                    ),
                )
            val p1 = participantRepository.save(Participant(party = party, user = user1))
            val p2 = participantRepository.save(Participant(party = party, user = user2))
            profileRepository.save(RealtimeParticipantProfile(participant = p1, nickname = "해파리1"))
            profileRepository.save(RealtimeParticipantProfile(participant = p2, nickname = "해파리2"))

            val result = profileRepository.findAllByPartyIdOrderByIdAsc(party.id)

            assertEquals(2, result.size)
            assertEquals("해파리1", result[0].nickname)
            assertEquals("해파리2", result[1].nickname)
        }

        @Test
        fun `findAllByPartyIdOrderByIdAsc - 다른 파티 프로필은 제외`() {
            val party1 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p1", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val party2 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p2", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user =
                userRepository.save(
                    User(
                        name = "해파리",
                        birthDay = "01-01",
                        email = "x@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "x",
                    ),
                )
            val pA = participantRepository.save(Participant(party = party1, user = user))
            val pB = participantRepository.save(Participant(party = party2, user = user))
            profileRepository.save(RealtimeParticipantProfile(participant = pA, nickname = "A"))
            profileRepository.save(RealtimeParticipantProfile(participant = pB, nickname = "B"))

            val result = profileRepository.findAllByPartyIdOrderByIdAsc(party1.id)

            assertEquals(listOf("A"), result.map { it.nickname })
        }

        @Test
        fun `findAllByParticipantIdIn - 주어진 participantId 들의 프로필을 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user1 =
                userRepository.save(
                    User(
                        name = "해A",
                        birthDay = "01-01",
                        email = "1@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "1",
                    ),
                )
            val user2 =
                userRepository.save(
                    User(
                        name = "해B",
                        birthDay = "02-02",
                        email = "2@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "2",
                    ),
                )
            val p1 = participantRepository.save(Participant(party = party, user = user1))
            val p2 = participantRepository.save(Participant(party = party, user = user2))
            profileRepository.save(RealtimeParticipantProfile(participant = p1, nickname = "해A"))
            profileRepository.save(RealtimeParticipantProfile(participant = p2, nickname = "해B"))

            val result = profileRepository.findAllByParticipantIdIn(listOf(p1.id, p2.id))

            assertEquals(2, result.size)
            assertTrue(result.any { it.nickname == "해A" })
            assertTrue(result.any { it.nickname == "해B" })
        }

        @Test
        fun `findAllByParticipantIdIn - 빈 컬렉션이면 빈 리스트`() {
            val result = profileRepository.findAllByParticipantIdIn(emptyList())
            assertTrue(result.isEmpty())
        }

        @Test
        fun `existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant - 대소문자 무시하고 같은 파티 내 중복을 감지`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val userA =
                userRepository.save(
                    User(
                        name = "A",
                        birthDay = "01-01",
                        email = "a@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "a",
                    ),
                )
            val userB =
                userRepository.save(
                    User(
                        name = "B",
                        birthDay = "02-02",
                        email = "b@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "b",
                    ),
                )
            val pA = participantRepository.save(Participant(party = party, user = userA))
            val pB = participantRepository.save(Participant(party = party, user = userB))
            profileRepository.save(RealtimeParticipantProfile(participant = pA, nickname = "Alpha"))

            val duplicated =
                profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                    partyId = party.id,
                    nickname = "alpha",
                    excludingParticipantId = pB.id,
                )

            assertTrue(duplicated)
        }

        @Test
        fun `existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant - 자기 자신의 nickname은 중복으로 보지 않는다`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user =
                userRepository.save(
                    User(
                        name = "U",
                        birthDay = "01-01",
                        email = "u@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "u",
                    ),
                )
            val participant = participantRepository.save(Participant(party = party, user = user))
            profileRepository.save(RealtimeParticipantProfile(participant = participant, nickname = "Same"))

            val duplicated =
                profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                    partyId = party.id,
                    nickname = "same",
                    excludingParticipantId = participant.id,
                )

            assertFalse(duplicated)
        }

        @Test
        fun `existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant - 다른 파티의 동일 nickname은 무시`() {
            val party1 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p1", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val party2 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p2", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val userA =
                userRepository.save(
                    User(
                        name = "A",
                        birthDay = "01-01",
                        email = "aa@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "aa",
                    ),
                )
            val userB =
                userRepository.save(
                    User(
                        name = "B",
                        birthDay = "02-02",
                        email = "bb@x",
                        provider = AuthProvider.KAKAO,
                        providerId = "bb",
                    ),
                )
            val pInParty1 = participantRepository.save(Participant(party = party1, user = userA))
            val pInParty2 = participantRepository.save(Participant(party = party2, user = userB))
            profileRepository.save(RealtimeParticipantProfile(participant = pInParty1, nickname = "Same"))

            val duplicated =
                profileRepository.existsByPartyIdAndNicknameIgnoreCaseExcludingParticipant(
                    partyId = party2.id,
                    nickname = "same",
                    excludingParticipantId = pInParty2.id,
                )

            assertFalse(duplicated)
        }
    }
