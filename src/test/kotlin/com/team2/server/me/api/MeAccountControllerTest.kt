package com.team2.server.me.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.config.TestcontainersConfiguration
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
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MeAccountControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            userRepository.deleteAll()
        }

        @Test
        fun `인증 없이 마이페이지 계정 조회 시 401`() {
            mockMvc.get("/api/me/account").andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        fun `유효한 JWT 로 마이페이지 계정 정보를 조회한다`() {
            val user = saveUser("kakao-me-account-1", "me-account-1@kakao.local", name = "김이라")
            user.createdAt = LocalDateTime.of(2026, 2, 23, 10, 0)
            userRepository.saveAndFlush(user)
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/me/account") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value(200) }
                    jsonPath("$.data.nickname") { value("김이라") }
                    jsonPath("$.data.provider") { value("KAKAO") }
                    jsonPath("$.data.connectedAt") { value("2026-02-23") }
                    jsonPath("$.data.supportChatUrl") { value("https://open.kakao.com/o/test-support") }
                }
        }

        @Test
        fun `토큰의 userId 가 DB 에 없으면 401`() {
            val user = saveUser("kakao-me-account-2", "me-account-2@kakao.local", name = "삭제예정")
            val token = tokenProvider.issue(user)
            userRepository.deleteById(user.id)
            userRepository.flush()

            mockMvc
                .get("/api/me/account") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
            name: String = "조회자",
        ): User =
            userRepository.save(
                User(
                    name = name,
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
