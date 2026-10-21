package com.team2.server.calendar.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.calendar.application.service.ConsentSubject
import com.team2.server.calendar.application.service.ConsentTicketSigner
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RETURN_PATH = "/party/366"
private const val WEB_BASE_URL = "https://test.example.com"

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
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
        private val partyRepository: PartyRepository,
        private val partyInviteRepository: PartyInviteRepository,
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
                    param("returnPath", RETURN_PATH)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.consentUrl") { exists() }
                }
        }

        @Test
        fun `경로가 아닌 returnPath 로 발급을 요청하면 400`() {
            val user = saveUser()

            mockMvc
                .get("/api/v1/me/talk-calendar-connection/consent-url") {
                    header("Authorization", "Bearer ${tokenProvider.issue(user)}")
                    param("returnPath", "https://evil.example.com/callback")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }

        @Test
        fun `진입 요청은 카카오 인가 주소로 리다이렉트하고 쿠키를 심는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", ticket)
                    param("return_path", RETURN_PATH)
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
                    param("return_path", RETURN_PATH)
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
        fun `경로가 아닌 return_path 로 진입하면 failed 로 돌려보낸다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent") {
                    param("ticket", ticket)
                    param("return_path", "//evil.example.com")
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=failed*")
                    cookie { doesNotExist("kakao_calendar_consent_ticket") }
                }
        }

        @Test
        fun `콜백은 토큰을 저장하고 granted 로 돌려보낸다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            RETURN_PATH,
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
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))
            KakaoStubServers.kakaoUserId = 999L

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            RETURN_PATH,
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
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", "other-ticket"))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            RETURN_PATH,
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=expired*")
                }

            assertTrue(connectionRepository.findAll().isEmpty())
        }

        @Test
        fun `복귀 경로 쿠키가 경로 형식이 아니면 그리로 보내지 않는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            "https://evil.example.com/callback",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrl("$WEB_BASE_URL/?calendarConsent=granted")
                }
        }

        @Test
        fun `이미 인코딩된 복귀 경로를 다시 인코딩하지 않는다`() {
            val user = saveUser()
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            "/party/366?q=%20x",
                        ),
                    )
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrl("$WEB_BASE_URL/party/366?q=%20x&calendarConsent=granted")
                }
        }

        @Test
        fun `사용자가 동의를 거부하면 denied 로 돌려보낸다`() {
            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("error", "access_denied")
                    cookie(
                        jakarta.servlet.http.Cookie(
                            "kakao_calendar_consent_return_path",
                            RETURN_PATH,
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
            val ticket = consentTicketSigner.issue(ConsentSubject.Member(user.id))
            mockMvc.get("/api/v1/kakao-calendar/consent/callback") {
                param("code", "auth-code")
                param("state", ticket)
                cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                cookie(
                    jakarta.servlet.http.Cookie(
                        "kakao_calendar_consent_return_path",
                        RETURN_PATH,
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

        @Test
        fun `비회원 콜백은 일정을 만들고 registered 로 돌려보낸다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    status { is3xxRedirection() }
                    redirectedUrlPattern("**/*calendarConsent=registered*")
                }

            val saved = calendarRegistrationRepository.findAll().single()
            assertEquals(fixture.partyId, saved.partyId)
            assertNull(saved.userId)
            assertNotNull(saved.kakaoProviderId)
            assertNotNull(saved.eventId)
            assertNotNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `같은 비회원이 다시 동의하면 already_registered 로 돌려보낸다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            guestCallback(fixture.inviteToken)
            KakaoStubServers.reset()

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    redirectedUrlPattern("**/*calendarConsent=already_registered*")
                }

            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `없는 초대 토큰이면 invite_invalid 로 돌려보낸다`() {
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest("nosuchtk"))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    redirectedUrlPattern("**/*calendarConsent=invite_invalid*")
                }

            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `비회원이 동의를 거부하면 denied 로 돌려보내고 등록이 없다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    redirectedUrlPattern("**/*calendarConsent=denied*")
                }

            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `비회원 티켓과 state 가 다르면 expired 로 돌려보낸다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(fixture.inviteToken))
            // 식별자를 다르게 둬 같은 초 안에 발급돼도 서명이 달라지게 한다 (티켓은 초 단위 타임스탬프를 쓴다).
            val other = consentTicketSigner.issue(ConsentSubject.Guest("${fixture.inviteToken}-other"))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", other)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    redirectedUrlPattern("**/*calendarConsent=expired*")
                }

            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        /** 첫 테스트의 콜백 호출을 그대로 수행하는 헬퍼다. 등록이 이미 끝났다는 전제로 재사용한다. */
        private fun guestCallback(inviteToken: String) {
            val ticket = consentTicketSigner.issue(ConsentSubject.Guest(inviteToken))

            mockMvc
                .get("/api/v1/kakao-calendar/consent/callback") {
                    param("code", "auth-code")
                    param("state", ticket)
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_ticket", ticket))
                    cookie(jakarta.servlet.http.Cookie("kakao_calendar_consent_return_path", RETURN_PATH))
                }.andExpect {
                    redirectedUrlPattern("**/*calendarConsent=registered*")
                }
        }

        private data class GuestFixture(
            val partyId: Long,
            val inviteToken: String,
        )

        private var inviteSequence = 0

        private fun saveHostPartyAndInvite(startedAt: LocalDateTime): GuestFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "consent-host",
                        email = "consent-host@test.local",
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
            val token = "cnst%010d".format(inviteSequence++)
            val invite =
                partyInviteRepository.save(
                    PartyInvite(party = party, token = token, expiresAt = LocalDateTime.now().plusDays(7)),
                )
            return GuestFixture(partyId = party.id, inviteToken = invite.token)
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
