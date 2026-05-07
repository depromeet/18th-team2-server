package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        @Test
        fun `비로그인 호출 시 200과 빈 보관함 응답을 반환한다`() {
            mockMvc.get("/api/v1/archive").andExpect {
                status { isOk() }
                jsonPath("$.data.items.length()") { value(0) }
                jsonPath("$.data.nextCursor") { value(nullValue()) }
                jsonPath("$.data.totalCount") { value(0) }
            }
        }

        @Test
        fun `인증된 사용자의 보관함이 비어있으면 빈 응답을 반환한다`() {
            val user = saveUser("kakao-archive-empty", "archive-empty@kakao.local")
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(0) }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "조회자",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
