package com.team2.server.calendar.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
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
import org.springframework.test.web.servlet.post
import java.net.URLDecoder
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
        private val kakaoCalendarConnectionRepository: KakaoCalendarConnectionRepository,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            KakaoStubServers.reset()
        }

        @Test
        fun `인증 없이 요청하면 401`() {
            mockMvc
                .post("/api/v1/parties/1/talk-calendar")
                .andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `연동이 없으면 403 과 동의 필요 코드를 반환한다`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("KAKAO_CALENDAR_CONSENT_REQUIRED") }
                }
        }

        @Test
        fun `존재하지 않는 파티면 404`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            saveValidConnection(fixture.hostId)

            mockMvc
                .post("/api/v1/parties/999999/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `이미 시작된 파티면 409`() {
            val fixture = saveHostAndParty(LocalDateTime.now().minusHours(1))
            saveValidConnection(fixture.hostId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
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
            saveValidConnection(stranger.id)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${tokenProvider.issue(stranger)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `호스트가 유효한 토큰으로 요청하면 일정을 만들고 200`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            saveValidConnection(fixture.hostId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.eventId") { value("stub-event-1") }
                    jsonPath("$.data.updated") { value(false) }
                }

            val saved = calendarRegistrationRepository.findAll().single()
            assertEquals(fixture.partyId, saved.partyId)
            assertEquals("stub-event-1", saved.eventId)
        }

        @Test
        fun `이미 등록한 파티를 다시 요청하면 기존 일정을 갱신하고 updated true`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            saveValidConnection(fixture.hostId)
            val request: (Unit) -> Unit = {
                mockMvc.post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }
            }
            request(Unit)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.eventId") { value("stub-event-1") }
                    jsonPath("$.data.updated") { value(true) }
                }

            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertNotNull(KakaoStubServers.requests[KakaoStubServers.UPDATE_EVENT_PATH])
        }

        @Test
        fun `카카오가 저장된 토큰을 거부하면 연동을 지우고 403 을 준다`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
            saveValidConnection(fixture.hostId)
            KakaoStubServers.rejectNextCreateEventWithUnauthorized = true

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("KAKAO_CALENDAR_CONSENT_REQUIRED") }
                }

            assertTrue(kakaoCalendarConnectionRepository.findAll().isEmpty())
        }

        @Test
        fun `카카오로 나가는 일정 시각은 카카오 문서 형식을 따른다`() {
            val startedAt = LocalDateTime.of(2026, 12, 24, 19, 0)
            val fixture = saveHostAndParty(startedAt)
            saveValidConnection(fixture.hostId)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect { status { isOk() } }

            val body =
                URLDecoder.decode(
                    KakaoStubServers.requests.getValue(KakaoStubServers.CREATE_EVENT_PATH),
                    Charsets.UTF_8,
                )
            // KST 19:00 -> UTC 10:00, 확장 ISO 8601 (2026-12-24T10:00:00Z)
            assertTrue(body.contains("\"start_at\":\"2026-12-24T10:00:00Z\""), body)
            assertTrue(body.contains("\"end_at\":\"2026-12-24T10:30:00Z\""), body)
            assertTrue(body.contains("\"time_zone\":\"Asia/Seoul\""), body)
        }

        private data class HostFixture(
            val partyId: Long,
            val hostId: Long,
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
            return HostFixture(partyId = party.id, hostId = host.id, hostToken = tokenProvider.issue(host))
        }

        /**
         * 토큰 확보 단계를 통과시키는 유효한 연동을 심어 둔다. party 검증(404/409/403)뿐 아니라
         * 카카오로 실제 요청이 나가는 등록 성공 경로에서도 그대로 재사용한다.
         */
        private fun saveValidConnection(userId: Long) {
            kakaoCalendarConnectionRepository.save(
                KakaoCalendarConnection(
                    userId = userId,
                    accessToken = "valid-access-token",
                    refreshToken = "valid-refresh-token",
                    accessTokenExpiresAt = LocalDateTime.now().plusHours(1),
                    refreshTokenExpiresAt = LocalDateTime.now().plusDays(30),
                ),
            )
        }

        /**
         * 실제 카카오 대신 루프백 스텁을 띄운다. 빈을 교체하지 않으므로 Spring 컨텍스트 fingerprint 가
         * 그대로라 캐시가 유지된다(docs/testing-rules.md 의 @MockitoBean 금지 규칙을 우회하지 않고 지킨다).
         *
         * [KakaoCalendarConsentFlowTest] 와 같은 포트를 쓰므로 [KakaoStubServers] 로 참조 계수 관리한다.
         */
        companion object {
            @JvmStatic
            @BeforeAll
            fun startStub() = KakaoStubServers.start()

            @JvmStatic
            @AfterAll
            fun stopStub() = KakaoStubServers.stop()
        }
    }
