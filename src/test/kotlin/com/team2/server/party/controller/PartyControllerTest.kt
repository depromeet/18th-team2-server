package com.team2.server.party.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.party.entity.Character
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class PartyControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
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
            defaultCharacterId = characterRepository.save(Character(name = "Default")).id
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
                          "startedDate": "2026-04-28",
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

            mockMvc
                .post("/api/v1/parties/realtime") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "celebrantNickname": "홍길동",
                          "startedDate": "2026-04-28",
                          "startTime": "14:30",
                          "characterId": $defaultCharacterId
                        }
                        """.trimIndent()
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.partyId") { isNumber() }
                }
        }

        @Test
        fun `PAPER_ONLY 파티 생성 성공 - startTime 없이 가능`() {
            val token = tokenProvider.issue(saveUser("kakao-create-2", "create2@kakao.local"))

            mockMvc
                .post("/api/v1/parties/paper-only") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "celebrantNickname": "홍길동",
                          "startedDate": "2026-04-28"
                        }
                        """.trimIndent()
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.partyId") { isNumber() }
                }
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
            val partyId = createParty(token, "2000-01-01", "00:00")

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
    }
