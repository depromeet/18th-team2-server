package com.team2.server.rollingpaper.repository

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyPurpose
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperTopping
import com.team2.server.support.JpaSliceTestSupport
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RollingPaperRepositoryTest
    @Autowired
    constructor(
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperToppingRepository: RollingPaperToppingRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) : JpaSliceTestSupport() {
        @Test
        fun `findByWriter - 작성자 participant 기준 단건 조회`() {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "p",
                        celebrantNickname = "홍",
                        purpose = PartyPurpose.BIRTHDAY,
                        startedAt = LocalDateTime.now(),
                    ),
                )
            val user =
                userRepository.save(
                    User(
                        name = "해파리",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "writer-1",
                        email = "a@a",
                    ),
                )
            val participant = participantRepository.save(Participant(party = party, user = user))
            val topping = rollingPaperToppingRepository.save(RollingPaperTopping(name = "Topping_Candle"))
            rollingPaperRepository.save(
                RollingPaper(
                    topping = topping,
                    writer = participant,
                    party = party,
                    writerNickname = "해파리",
                    content = "축하해",
                ),
            )

            val result = rollingPaperRepository.findByWriter(participant)

            assertEquals("축하해", result?.content)
            assertEquals("해파리", result?.writerNickname)
        }

        @Test
        fun `findByWriter - 작성 안 한 participant는 null`() {
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
                        name = "비작성자",
                        birthDay = "02-02",
                        provider = AuthProvider.KAKAO,
                        providerId = "writer-none",
                        email = "b@b",
                    ),
                )
            val participant = participantRepository.save(Participant(party = party, user = user))

            val result = rollingPaperRepository.findByWriter(participant)

            assertNull(result)
        }
    }
