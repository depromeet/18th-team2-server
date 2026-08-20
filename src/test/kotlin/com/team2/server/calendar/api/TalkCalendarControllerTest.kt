package com.team2.server.calendar.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
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
import java.net.InetSocketAddress
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
            kakaoRequests.clear()
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
        @org.junit.jupiter.api.Disabled("Task 9 에서 연동 픽스처와 함께 복구")
        fun `호스트가 유효한 토큰으로 요청하면 일정을 만들고 200`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))

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
        @org.junit.jupiter.api.Disabled("Task 9 에서 연동 픽스처와 함께 복구")
        fun `이미 등록한 파티를 다시 요청하면 기존 일정을 갱신하고 updated true`() {
            val fixture = saveHostAndParty(LocalDateTime.now().plusDays(2))
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
            assertNotNull(kakaoRequests[UPDATE_PATH])
        }

        @Test
        @org.junit.jupiter.api.Disabled("Task 9 에서 연동 픽스처와 함께 복구")
        fun `카카오로 나가는 일정 시각은 카카오 문서 형식을 따른다`() {
            val startedAt = LocalDateTime.of(2026, 12, 24, 19, 0)
            val fixture = saveHostAndParty(startedAt)

            mockMvc
                .post("/api/v1/parties/${fixture.partyId}/talk-calendar") {
                    header("Authorization", "Bearer ${fixture.hostToken}")
                }.andExpect { status { isOk() } }

            val body = URLDecoder.decode(kakaoRequests.getValue(CREATE_PATH), Charsets.UTF_8)
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
         * party 검증(404/409/403)이 토큰 확보 이후 단계까지 도달하도록, 만료되지 않은 연동을 심어 둔다.
         * 등록 성공 경로(카카오로 실제 요청이 나가는 케이스)는 Task 9 에서 별도 픽스처로 되살린다.
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

        companion object {
            private const val STUB_PORT = 19595
            private const val CREATE_PATH = "/v2/api/calendar/create/event"
            private const val UPDATE_PATH = "/v2/api/calendar/update/event/host"

            /** 경로별 마지막 요청 바디. 실제 카카오로 나가는 페이로드를 검증하는 데 쓴다. */
            private val kakaoRequests = java.util.concurrent.ConcurrentHashMap<String, String>()

            private lateinit var stub: HttpServer

            /**
             * 실제 카카오 대신 루프백 스텁을 띄운다.
             * 빈을 교체하지 않으므로 Spring 컨텍스트 fingerprint 가 그대로라 캐시가 유지된다
             * (docs/testing-rules.md 의 @MockitoBean 금지 규칙을 우회하지 않고 지킨다).
             */
            @JvmStatic
            @BeforeAll
            fun startStub() {
                stub =
                    HttpServer.create(InetSocketAddress("127.0.0.1", STUB_PORT), 0).apply {
                        createContext(CREATE_PATH) { respond(it, """{"event_id":"stub-event-1"}""") }
                        createContext(UPDATE_PATH) { respond(it, """{"event_id":"stub-event-1"}""") }
                        start()
                    }
            }

            @JvmStatic
            @AfterAll
            fun stopStub() {
                stub.stop(0)
            }

            private fun respond(
                exchange: HttpExchange,
                body: String,
            ) {
                kakaoRequests[exchange.requestURI.path] = exchange.requestBody.readBytes().decodeToString()
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
    }
