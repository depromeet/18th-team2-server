package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PartyPhaseControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val phaseStore: PartyPhaseStore,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            phaseStore.clear()
        }

        @Test
        fun `호스트가 GET phase 조회 시 ENTRY 반환`() {
            val fixture = saveHostAndParty()

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/phase") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("ENTRY") }
                    jsonPath("$.data.phaseStartedAt") { exists() }
                    jsonPath("$.data.serverNow") { exists() }
                }
        }

        @Test
        fun `호스트가 ENTRY→MUSIC advance 성공`() {
            val fixture = saveHostAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"currentPhase":"ENTRY"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("MUSIC") }
                }
        }

        @Test
        fun `참여자 token으로 GET phase 조회 성공`() {
            val fixture = saveParticipantAndParty()
            phaseStore.advance(fixture.partyId, PartyPhase.ENTRY, PartyPhase.MUSIC, LocalDateTime.now())

            mockMvc
                .get("/api/v1/parties/${fixture.partyId}/phase") {
                    header("X-Participant-Token", fixture.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.phase") { value("MUSIC") }
                }
        }

        @Test
        fun `비호스트가 ENTRY→MUSIC advance 시 403`() {
            val fixture = saveParticipantAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("X-Participant-Token", fixture.participantToken)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"currentPhase":"ENTRY"}"""
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `허용되지 않는 currentPhase 시 400`() {
            val fixture = saveHostAndParty()

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/phase/advance") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"currentPhase":"BURST"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        private data class HostFixture(val partyId: Long, val hostToken: String)

        private data class ParticipantFixture(val partyId: Long, val participantToken: String)

        private fun saveHostAndParty(): HostFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "phase-host-1",
                        email = "phase-host-1@test.local",
                    ),
                )
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = host.id,
                        startedAt = LocalDateTime.now().minusMinutes(1),
                    ),
                )
            return HostFixture(partyId = party.id, hostToken = tokenProvider.issue(host))
        }

        private fun saveParticipantAndParty(): ParticipantFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "phase-host-2",
                        email = "phase-host-2@test.local",
                    ),
                )
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = host.id,
                        startedAt = LocalDateTime.now().minusMinutes(1),
                    ),
                )
            val guest =
                userRepository.save(
                    User(
                        name = "게스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "phase-guest-1",
                        email = "phase-guest-1@test.local",
                    ),
                )
            val participant = participantRepository.save(Participant(party = party, user = guest))
            val profile =
                profileRepository.save(
                    RealtimeParticipantProfile(
                        participant = participant,
                        nickname = "게스트",
                        participantToken = "phasegt1",
                    ),
                )
            return ParticipantFixture(partyId = party.id, participantToken = profile.participantToken)
        }
    }
