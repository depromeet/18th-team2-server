package com.team2.server.party.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class PartyInviteLookupControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
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
        fun `인증 없이 PAPER_ONLY 초대장 조회 성공 및 participant 미생성`() {
            val createdAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                    createdAt,
                )
            saveInvite(party, "paperlookup0001")

            mockMvc.get("/api/v1/party-invites/paperlookup0001").andExpect {
                status { isOk() }
                jsonPath("$.status") { value(200) }
                jsonPath("$.data.partyId") { doesNotExist() }
                jsonPath("$.data.celebrantNickname") { value("홍길동") }
                jsonPath("$.data.partyOption") { value("PAPER_ONLY") }
                jsonPath("$.data.partyEnded") { value(false) }
                jsonPath("$.data.rollingPaperWritten") { value(false) }
                jsonPath("$.data.partyStartDate") { value(createdAt.toLocalDate().toString()) }
                jsonPath("$.data.partyEndDate") {
                    value(
                        createdAt
                            .plusDays(Party.ENDED_AFTER_DAYS)
                            .toLocalDate()
                            .toString(),
                    )
                }
                jsonPath("$.data.realtimeSchedule") { value(nullValue()) }
            }

            assertEquals(0, participantRepository.count())
        }

        @Test
        fun `생성 후 7일이 지난 파티는 partyEnded true`() {
            val createdAt = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                    createdAt,
                )
            saveInvite(party, "endedparty00001")

            mockMvc.get("/api/v1/party-invites/endedparty00001").andExpect {
                status { isOk() }
                jsonPath("$.data.partyEnded") { value(true) }
            }
        }

        @Test
        fun `만료된 초대 토큰도 초대장 조회 가능`() {
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                    LocalDateTime.now().minusDays(1),
                )
            saveInvite(party, "expiredlookup1", LocalDateTime.now().minusHours(1))

            mockMvc.get("/api/v1/party-invites/expiredlookup1").andExpect {
                status { isOk() }
                jsonPath("$.data.partyId") { doesNotExist() }
            }
        }

        @Test
        fun `인증 회원이 이미 롤페를 작성했으면 rollingPaperWritten true`() {
            val user = saveUser("kakao-lookup-written", "lookup-written@kakao.local")
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                    LocalDateTime.now().minusDays(1),
                )
            participantRepository.save(
                Participant(
                    party = party,
                    user = user,
                    hasWrittenPaper = true,
                ),
            )
            saveInvite(party, "writtenlookup01")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/writtenlookup01") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.rollingPaperWritten") { value(true) }
                }
        }

        @Test
        fun `REALTIME 초대장 조회는 실시간 일정 기준 시각을 내려준다`() {
            val liveStartAt = LocalDateTime.now().plusMinutes(4).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = liveStartAt,
                    ),
                    LocalDateTime.now().minusDays(1),
                )
            saveInvite(party, "enterable000001")

            mockMvc.get("/api/v1/party-invites/enterable000001").andExpect {
                status { isOk() }
                jsonPath("$.data.partyOption") { value("REALTIME") }
                jsonPath("$.data.realtimeSchedule.liveStartAt") {
                    value(liveStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                }
                jsonPath("$.data.realtimeSchedule.enterableFrom") {
                    value(
                        liveStartAt
                            .minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    )
                }
                jsonPath("$.data.realtimeSchedule.liveEndAt") {
                    value(
                        liveStartAt
                            .plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    )
                }
                jsonPath("$.data.realtimeSchedule.liveDurationMinutes") {
                    value(RealtimeParty.LIVE_DURATION_MINUTES)
                }
            }
        }

        @Test
        fun `없는 초대 토큰이면 404`() {
            mockMvc.get("/api/v1/party-invites/missingtoken000").andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
            }
        }

        @Test
        fun `잘못된 Bearer 토큰이면 공개 조회 API도 401`() {
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                    LocalDateTime.now().minusDays(1),
                )
            saveInvite(party, "invalidbearer01")

            mockMvc
                .get("/api/v1/party-invites/invalidbearer01") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        private fun saveParty(
            party: Party,
            createdAt: LocalDateTime,
        ): Party {
            val saved = partyRepository.saveAndFlush(party)
            saved.createdAt = createdAt
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveInvite(
            party: Party,
            token: String,
            expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        ): PartyInvite =
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    token = token,
                    expiresAt = expiresAt,
                ),
            )

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
