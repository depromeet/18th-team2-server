package com.team2.server.party.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PartyControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
        private val userRepository: UserRepository,
        private val characterRepository: CharacterRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private val objectMapper = ObjectMapper()
        private var defaultCharacterId: Long = 1L

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            defaultCharacterId = characterRepository.save(Character(name = "blue")).id
        }

        @Test
        fun `인증 없이 실시간 파티 생성 시 401`() {
            mockMvc
                .post("/api/v1/parties/realtime") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "celebrantNickname": "홍길동",
                          "startedDate": "2099-04-28",
                          "startTime": "14:30",
                          "characterId": 1
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `인증 없이 롤링페이퍼 파티 생성 시 401`() {
            mockMvc
                .post("/api/v1/parties/paper-only") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "celebrantNickname": "홍길동",
                          "startedDate": "2026-04-28",
                          "startTime": "14:30"
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `REALTIME 파티 생성 성공`() {
            val token = tokenProvider.issue(saveUser("kakao-create-1", "create@kakao.local"))

            val result =
                mockMvc
                    .post("/api/v1/parties/realtime") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """
                            {
                              "celebrantNickname": "홍길동",
                              "startedDate": "2099-04-28",
                              "startTime": "14:30",
                              "characterId": $defaultCharacterId
                            }
                            """.trimIndent()
                        header("Authorization", "Bearer $token")
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.data.partyId") { isNumber() }
                    }.andReturn()
            val partyId = objectMapper.readTree(result.response.contentAsString)["data"]["partyId"].asLong()

            assertDefaultInviteCreated(partyId)
        }

        @Test
        fun `PAPER_ONLY 파티 생성 성공 - startTime 없이 가능`() {
            val token = tokenProvider.issue(saveUser("kakao-create-2", "create2@kakao.local"))

            val result =
                mockMvc
                    .post("/api/v1/parties/paper-only") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """
                            {
                              "celebrantNickname": "홍길동",
                              "startedDate": "2099-04-28"
                            }
                            """.trimIndent()
                        header("Authorization", "Bearer $token")
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.data.partyId") { isNumber() }
                    }.andReturn()
            val partyId = objectMapper.readTree(result.response.contentAsString)["data"]["partyId"].asLong()

            assertDefaultInviteCreated(partyId)
        }

        @Test
        fun `존재하지 않는 파티 유형으로 생성 시 404`() {
            val token = tokenProvider.issue(saveUser("kakao-invalid-1", "invalid@kakao.local"))

            mockMvc
                .post("/api/v1/parties/invalid-type") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"celebrantNickname": "홍길동", "startedDate": "2026-04-28", """ +
                        """"startTime": "14:30", "characterId": $defaultCharacterId}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isNotFound() }
                }
        }

        @Test
        fun `인증 없이 파티 삭제 시 401`() {
            mockMvc
                .delete("/api/v1/parties/1")
                .andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `존재하지 않는 파티 삭제 시 404`() {
            val token = tokenProvider.issue(saveUser("kakao-del-notfound", "del-notfound@kakao.local"))

            mockMvc
                .delete("/api/v1/parties/99999") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isNotFound() }
                }
        }

        @Test
        fun `주최자가 아닌 유저가 삭제 시 403`() {
            val ownerToken = tokenProvider.issue(saveUser("kakao-del-owner", "del-owner@kakao.local"))
            val otherToken = tokenProvider.issue(saveUser("kakao-del-other", "del-other@kakao.local"))

            val partyId = createParty(ownerToken, "2099-12-31", "14:30")

            mockMvc
                .delete("/api/v1/parties/$partyId") {
                    header("Authorization", "Bearer $otherToken")
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `이미 시작된 파티 삭제 시 409`() {
            val token = tokenProvider.issue(saveUser("kakao-del-started", "del-started@kakao.local"))
            val startedAt = LocalDateTime.now().minusMinutes(1)
            val startTime = startedAt.toLocalTime().withSecond(0).withNano(0)
            val partyId = createParty(token, startedAt.toLocalDate().toString(), startTime.toString())

            mockMvc
                .delete("/api/v1/parties/$partyId") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isConflict() }
                }
        }

        @Test
        fun `파티 시작 전 주최자가 삭제 시 200`() {
            val token = tokenProvider.issue(saveUser("kakao-del-success", "del-success@kakao.local"))
            val partyId = createParty(token, "2099-12-31", "14:30")

            mockMvc
                .delete("/api/v1/parties/$partyId") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                }

            assertEquals(false, partyRepository.existsById(partyId))
            assertEquals(0, participantRepository.findAllByPartyId(partyId).size)
        }

        @Test
        fun `주최자는 LIVE_OPEN이면 4분 전에도 HOST_LEFT로 실시간 파티 종료를 시작할 수 있다`() {
            val owner = saveUser("kakao-realtime-end-start", "end-start@kakao.local")
            val party = saveRealtimeParty(owner, LocalDateTime.now().minusMinutes(1))

            mockMvc
                .post("/api/v1/parties/${party.id}/realtime-end") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(party.id.toInt()) }
                    jsonPath("$.data.endingStartedAt") { exists() }
                    jsonPath("$.data.endedAt") { exists() }
                    jsonPath("$.data.endingReason") { value("HOST_LEFT") }
                    jsonPath("$.data.hostNickname") { value("주최자") }
                }
        }

        @Test
        fun `회원과 게스트 참가자는 실시간 파티 상태를 조회할 수 있다`() {
            val owner = saveUser("kakao-realtime-state-owner", "state-owner@kakao.local")
            val member = saveUser("kakao-realtime-state-member", "state-member@kakao.local")
            val party = saveRealtimeParty(owner, LocalDateTime.now().minusMinutes(1))
            val memberParticipant = participantRepository.save(Participant(party = party, user = member))
            val guestParticipant = participantRepository.save(Participant(party = party))
            val guestProfile =
                realtimeParticipantProfileRepository.save(
                    RealtimeParticipantProfile(
                        participant = guestParticipant,
                        nickname = "게스트",
                        participantToken = "guest001",
                    ),
                )

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-state") {
                    header("Authorization", "Bearer ${tokenProvider.issue(member)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(party.id.toInt()) }
                    jsonPath("$.data.status") { value("LIVE_OPEN") }
                    jsonPath("$.data.endingReason") { doesNotExist() }
                    jsonPath("$.data.hostNickname") { value("주최자") }
                }

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-state") {
                    header("X-Participant-Token", guestProfile.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(party.id.toInt()) }
                    jsonPath("$.data.status") { value("LIVE_OPEN") }
                }

            assertEquals(party.id, memberParticipant.party.id)
        }

        @Test
        fun `실시간 파티 상태는 주최자 종료 인사 가능 여부와 기준 시각을 제공한다`() {
            val owner = saveUser("kakao-realtime-state-farewell", "state-farewell@kakao.local")
            val liveStartedAt = LocalDateTime.now().minusMinutes(1)
            val party =
                saveRealtimeParty(owner, LocalDateTime.now().minusMinutes(1)).also {
                    it.liveStartedAt = liveStartedAt
                    partyRepository.save(it)
                }

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-state") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.hostFarewellAvailable") { value(false) }
                    jsonPath("$.data.hostFarewellAvailableAt") { exists() }
                    jsonPath("$.data.serverNow") { exists() }
                }
        }

        @Test
        fun `박터뜨리기가 종료된 파티 상태는 주최자 종료 인사 가능 상태를 복구한다`() {
            val owner = saveUser("kakao-realtime-state-burst-ended", "state-burst-ended@kakao.local")
            val party =
                saveRealtimeParty(owner, LocalDateTime.now().minusMinutes(1)).also {
                    it.liveStartedAt = LocalDateTime.now().minusMinutes(1)
                    it.burstGameEndedAt = LocalDateTime.now().minusSeconds(1)
                    partyRepository.save(it)
                }

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-state") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.hostFarewellAvailable") { value(true) }
                }
        }

        @Test
        fun `자동 종료 카운트다운 상태는 종료 원인과 주최자 닉네임을 조회할 수 있다`() {
            val owner = saveUser("kakao-realtime-state-ending", "state-ending@kakao.local")
            val startedAt = LocalDateTime.now().minusMinutes(10).minusSeconds(10)
            val party = saveRealtimeParty(owner, startedAt)
            party.liveStartedAt = startedAt
            partyRepository.save(party)

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-state") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.status") { value("LIVE_ENDING") }
                    jsonPath("$.data.endingReason") { value("TIME_LIMIT_REACHED") }
                    jsonPath("$.data.hostNickname") { value("주최자") }
                }
        }

        @Test
        fun `LIVE_OPEN 상태에서는 다음 행동 조회가 실패한다`() {
            val owner = saveUser("kakao-next-open", "next-open@kakao.local")
            val party = saveRealtimeParty(owner, LocalDateTime.now().minusMinutes(1))

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-next-action") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("REALTIME_PARTY_INVALID_STATE") }
                }
        }

        @Test
        fun `LIVE_CLOSED 상태에서는 주최자 다음 행동을 조회할 수 있다`() {
            val owner = saveUser("kakao-next-closed", "next-closed@kakao.local")
            val startedAt = LocalDateTime.now().minusMinutes(12)
            val party = saveRealtimeParty(owner, startedAt)
            party.liveStartedAt = startedAt
            partyRepository.save(party)

            mockMvc
                .get("/api/v1/parties/${party.id}/realtime-next-action") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.type") { value("HOST_ROLLING_PAPER_LIST") }
                    jsonPath("$.data.partyId") { value(party.id.toInt()) }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "파티장",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )

        private fun createParty(
            token: String,
            date: String,
            time: String,
        ): Long {
            val result =
                mockMvc
                    .post("/api/v1/parties/realtime") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"celebrantNickname": "홍길동", "startedDate": "$date", """ +
                            """"startTime": "$time", "characterId": $defaultCharacterId}"""
                        header("Authorization", "Bearer $token")
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            val body = result.response.contentAsString
            val node = objectMapper.readTree(body)
            return node["data"]["partyId"].asLong()
        }

        private fun assertDefaultInviteCreated(partyId: Long) {
            val invites =
                partyInviteRepository.findAllByPartyIdInAndExpiresAtAfter(
                    listOf(partyId),
                    LocalDateTime.now(),
                )
            assertEquals(1, invites.size)
        }

        private fun saveRealtimeParty(
            owner: User,
            startedAt: LocalDateTime,
        ): RealtimeParty {
            val party = partyRepository.save(RealtimeParty(ownerId = owner.id, startedAt = startedAt))
            val host = participantRepository.save(Participant(party = party, user = owner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(
                    participant = host,
                    nickname = "주최자",
                ),
            )
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    token = "invite${party.id.toString().padStart(10, '0')}",
                    expiresAt = LocalDateTime.now().plusDays(7),
                ),
            )
            return party
        }
    }
