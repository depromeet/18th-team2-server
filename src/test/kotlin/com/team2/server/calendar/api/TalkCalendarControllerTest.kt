package com.team2.server.calendar.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class TalkCalendarControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `인증 없이 요청하면 401`() {
            mockMvc
                .post("/api/v1/parties/1/talk-calendar") {
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `카카오 액세스 토큰 헤더가 없으면 400`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `존재하지 않는 파티면 404`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/999999/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `이미 시작된 파티면 409`() {
            val fixture = saveHostAndParty(LocalDateTime.now().minusHours(1))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("TALK_CALENDAR_PARTY_ALREADY_STARTED") }
                }
        }

        @Test
        fun `파티 멤버가 아니면 403`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            val stranger =
                userRepository.save(
                    User(
                        name = "남",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "calendar-stranger",
                        email = "calendar-stranger@test.local",
                    ),
                )

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${tokenProvider.issue(stranger)}")
                    header("X-Kakao-Access-Token", "kakao-token")
                }.andExpect {
                    status { isForbidden() }
                }
        }

        private data class HostFixture(
            val partyId: Long,
            val hostToken: String,
        )

        private fun saveHostAndParty(startedAt: LocalDateTime): HostFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "calendar-host",
                        email = "calendar-host@test.local",
                    ),
                )
            val party =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = host.id,
                        startedAt = startedAt,
                        celebrantNickname = "지민",
                    ),
                )
            return HostFixture(partyId = party.id, hostToken = tokenProvider.issue(host))
        }
    }
