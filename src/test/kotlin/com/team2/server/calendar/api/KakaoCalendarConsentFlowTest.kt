package com.team2.server.calendar.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.support.KakaoStubServers
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val WHITELISTED_REDIRECT_URI = "http://localhost:3000/oauth/redirect"

/**
 * 동의 URL 발급부터 진입, 콜백, 등록, 해제까지 전 구간을 실제 HTTP 로 관통해 검증한다.
 *
 * `MockRestServiceServer` 는 요청/응답 본문을 버퍼링해 전송 계층 차이를 재현하지 못한다. 카카오 시각
 * 포맷 오류와 오류 응답 본문 유실이 모두 단위 테스트를 통과하고 실제 HTTP 호출에서만 드러난 전례가 있어,
 * 이 테스트는 루프백 스텁 서버([KakaoStubServers])로 실제 소켓 통신을 거친다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class KakaoCalendarConsentFlowTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val userRepository: UserRepository,
        private val connectionRepository: KakaoCalendarConnectionRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
        private val consentTicketSigner: ConsentTicketSigner,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            KakaoStubServers.reset()
        }

        /** 어댑터가 카카오 회원번호를 숫자로 파싱하므로 providerId 도 숫자 문자열이어야 한다. */
        private fun saveUser(providerId: String = "1"): User =
            userRepository.save(
                User(
                    name = "호스트",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = "$providerId@test.local",
                ),
            )

        @Test
        fun `동의 URL 발급은 우리 진입 주소와 티켓을 담는다`() {
            val user = saveUser()

            mockMvc
                .get("/api/v1/me/talk-calendar-connection/consent-url") {
                    header("Authorization", "Bearer ${tokenProvider.issue(user)}")
                    param("redirectUri", WHITELISTED_REDIRECT_URI)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.consentUrl") { exists() }
                }
        }

        @Test
        fun `진입 요청은 카카오 인가 주소로 리다이렉트하고 쿠키를 심는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", ticket)
                    param("redirect_uri", WHITELISTED_REDIRECT_URI)
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("http://localhost:19596/oauth/authorize?*")
                    cookie { exists("kakao_calendar_consent_ticket") }
                }
        }

        @Test
        fun `위조된 티켓으로 진입하면 expired 로 돌려보낸다`() {
            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", "forged.ticket")
                    param("redirect_uri", WHITELISTED_REDIRECT_URI)
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=expired*")
                }
        }

        /**
         * 오픈 리다이렉트를 막는 유일한 지점이다. 티켓은 유효하지만 목적지가 화이트리스트 밖이면
         * 카카오로 보내지 않고 (화이트리스트의 첫 항목으로) failed 결과만 돌려보낸다.
         */
        @Test
        fun `화이트리스트에 없는 redirect_uri 로 진입하면 failed 로 돌려보낸다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", ticket)
                    param("redirect_uri", "http://evil.example.com/callback")
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=failed*")
                    cookie { doesNotExist("kakao_calendar_consent_ticket") }
                }
        }

        @Test
        fun `콜백은 토큰을 저장하고 granted 로 돌려보낸다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            WHITELISTED_REDIRECT_URI,
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=granted*")
                }

            val saved = connectionRepository.findAll().single()
            assertEquals(user.id, saved.userId)
            assertEquals("stub-access", saved.accessToken)
            assertEquals("stub-refresh", saved.refreshToken)
        }

        @Test
        fun `카카오 계정이 티켓의 사용자와 다르면 저장하지 않는다`() {
            val user = saveUser(providerId = "1")
            val ticket = consentTicketSigner.issue(user.id)
            KakaoStubServers.kakaoUserId = 999L

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            WHITELISTED_REDIRECT_URI,
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=account_mismatch*")
                }

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        @Test
        fun `state 와 쿠키 티켓이 다르면 저장하지 않는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", "other-ticket"))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            WHITELISTED_REDIRECT_URI,
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=expired*")
                }

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        @Test
        fun `사용자가 동의를 거부하면 denied 로 돌려보낸다`() {
            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("error", "access_denied")
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_redirect_uri",
                            WHITELISTED_REDIRECT_URI,
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=denied*")
                }
        }

        @Test
        fun `연동 해제는 저장된 토큰을 지운다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(user.id)
            mockMvc.get("/api/v1/kakao-calendar/consent/callback") {
                param("code", "auth-code")
                param("state", ticket)
                cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                cookie(
                    jakarta.servlet.http.Cookie(
                        "kakao_calendar_consent_redirect_uri",
                        WHITELISTED_REDIRECT_URI,
                    ),
                )
            }
            assertNotNull(connectionRepository.findAll().singleOrNull())

            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .delete("/api/v1/me/talk-calendar-connection")
                        .header("Authorization", "Bearer ${tokenProvider.issue(user)}"),
                ).andExpect(MockMvcResultMatchers.status().isNoContent)

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        companion object {
            @JvmStatic
            @BeforeAll
            fun startStub() = KakaoStubServers.start()

            @JvmStatic
            @AfterAll
            fun stopStub() = KakaoStubServers.stop()
        }
    }
