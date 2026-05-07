package com.team2.server.auth

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
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
        fun `actuator health 는 인증 없이 접근 가능`() {
            mockMvc.get("/actuator/health").andExpect {
                status { isOk() }
            }
        }

        @Test
        fun `api auth me 는 토큰 없이 401`() {
            mockMvc.get("/api/auth/me").andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
            }
        }

        @Test
        fun `api auth me 는 유효한 토큰으로 200`() {
            val user =
                userRepository.save(
                    User(
                        name = "n",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "kakao-int-1",
                        email = "int@kakao.local",
                    ),
                )
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/auth/me") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.id") { value(user.id) }
                    jsonPath("$.data.email") { value("int@kakao.local") }
                    jsonPath("$.data.provider") { value("KAKAO") }
                }
        }

        @Test
        fun `잘못된 토큰은 INVALID_TOKEN`() {
            mockMvc
                .get("/api/auth/me") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        @Test
        fun `oauth2 authorization kakao 는 카카오로 302 리다이렉트`() {
            mockMvc.get("/oauth2/authorization/kakao").andExpect {
                status { is3xxRedirection() }
                header { string("Location", org.hamcrest.Matchers.containsString("kauth.kakao.com")) }
            }
        }
    }
