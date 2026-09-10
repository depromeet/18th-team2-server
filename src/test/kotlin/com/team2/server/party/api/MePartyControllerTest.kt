package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcResultMatchersDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MePartyControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            partyInviteRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        @Test
        fun `인증 없이 다가오는 파티 목록 조회 시 401`() {
            mockMvc.get("/api/v1/me/upcoming-parties").andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        fun `내가 참여 중이고 종료되지 않은 파티 목록을 시작일 가까운 순으로 조회한다`() {
            val user = saveUser("kakao-upcoming-member", "upcoming-member@kakao.local")
            val other = saveUser("kakao-upcoming-other", "upcoming-other@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val liveStartAt = now.plusHours(3)
            val realtimeParty =
                saveParty(
                    RealtimeParty(
                        ownerId = other.id,
                        celebrantNickname = "실시간주인공",
                        startedAt = liveStartAt,
                    ),
                    now.minusDays(1),
                )
            val paperOnlyParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "롤페주인공",
                        startedAt = now.toLocalDate().plusDays(2).atStartOfDay(),
                    ),
                    now.minusHours(2),
                )
            val endedParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = other.id,
                        celebrantNickname = "종료주인공",
                        startedAt = now.minusDays(8).toLocalDate().atStartOfDay(),
                    ),
                    now.minusDays(8),
                )
            val otherParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = other.id,
                        celebrantNickname = "다른회원주인공",
                        startedAt = now.toLocalDate().plusDays(2).atStartOfDay(),
                    ),
                    now.minusHours(1),
                )

            saveParticipant(realtimeParty, user, hasWrittenPaper = true, createdAt = now.minusMinutes(20))
            saveParticipant(paperOnlyParty, user, createdAt = now.minusMinutes(5))
            saveParticipant(endedParty, user, createdAt = now.minusMinutes(1))
            saveParticipant(otherParty, other, createdAt = now)
            saveInvite(realtimeParty, "upcominglive001")
            saveInvite(paperOnlyParty, "upcomingpaper01")

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(2) }
                    expectRealtimeParty(realtimeParty, liveStartAt, index = 0)
                    expectPaperOnlyParty(paperOnlyParty, index = 1)
                }
        }

        @Test
        fun `미시작 파티가 이미 시작한 파티보다 먼저 노출된다`() {
            val user = saveUser("kakao-sort-order", "sort-order@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val startedParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "이미시작",
                        startedAt = now.minusDays(2),
                    ),
                    now.minusDays(3),
                )
            val futureParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "곧시작",
                        startedAt = now.plusHours(1),
                    ),
                    now.minusHours(1),
                )

            saveParticipant(startedParty, user, createdAt = now.minusDays(3))
            saveParticipant(futureParty, user, createdAt = now.minusHours(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(2) }
                    jsonPath("$.data[0].celebrantNickname") { value("곧시작") }
                    jsonPath("$.data[1].celebrantNickname") { value("이미시작") }
                }
        }

        @Test
        fun `유효한 초대 토큰이 없으면 inviteToken 은 null`() {
            val user = saveUser("kakao-upcoming-no-invite", "upcoming-no-invite@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "토큰없음",
                        startedAt = now.toLocalDate().plusDays(1).atStartOfDay(),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, createdAt = now)
            saveInvite(party, "expiredupcoming", now.minusMinutes(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(1) }
                    jsonPath("$.data[0].inviteToken") { value(nullValue()) }
                }
        }

        @Test
        fun `입장 가능 시각이 되면 realtimeEnterable true 와 LIVE_OPEN 을 내려준다`() {
            val user = saveUser("kakao-upcoming-open", "upcoming-open@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val liveStartAt = now.minusMinutes(1)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        celebrantNickname = "진행중",
                        startedAt = liveStartAt,
                    ).apply { liveStartedAt = liveStartAt },
                    now.minusDays(1),
                )
            saveParticipant(party, user, createdAt = now.minusHours(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(1) }
                    jsonPath("$.data[0].realtimeStatus") { value("LIVE_OPEN") }
                    jsonPath("$.data[0].realtimeEnterable") { value(true) }
                    jsonPath("$.data[0].realtimeSchedule.liveStartedAt") {
                        value(liveStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    }
                    jsonPath("$.data[0].realtimeSchedule.liveEndAt") {
                        value(
                            liveStartAt
                                .plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        )
                    }
                }
        }

        @Test
        fun `조기 종료된 실시간 파티는 예상 종료 시각 전이어도 종료 상태를 내려준다`() {
            val user = saveUser("kakao-upcoming-early-end", "upcoming-early-end@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val liveStartAt = now.minusMinutes(3)
            val liveEndingStartedAt = now.minusMinutes(2)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        celebrantNickname = "조기종료",
                        startedAt = liveStartAt,
                    ).apply {
                        liveStartedAt = liveStartAt
                        this.liveEndingStartedAt = liveEndingStartedAt
                    },
                    now.minusDays(1),
                )
            saveParticipant(party, user, createdAt = now.minusHours(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(1) }
                    jsonPath("$.data[0].realtimeStatus") { value("LIVE_CLOSED") }
                    jsonPath("$.data[0].realtimeEnterable") { value(false) }
                    jsonPath("$.data[0].realtimeSchedule.liveEndAt") {
                        value(liveEndingStartedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    }
                }
        }

        @Test
        fun `PAPER_ONLY 파티는 주최자 오픈 시각이 지나면 안내가 필요하다고 내려준다`() {
            val user = saveUser("kakao-notice-paper", "notice-paper@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "오픈됨",
                        startedAt = now.minusDays(1).toLocalDate().atStartOfDay(),
                    ),
                    now.minusDays(2),
                )
            saveParticipant(party, user, createdAt = now.minusDays(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(1) }
                    jsonPath("$.data[0].isHost") { value(true) }
                    jsonPath("$.data[0].hostRollingPaperNoticePending") { value(true) }
                }
        }

        @Test
        fun `주최자가 아니면 오픈 시각이 지나도 안내가 필요하지 않다`() {
            val user = saveUser("kakao-notice-guest", "notice-guest@kakao.local")
            val owner = saveUser("kakao-notice-owner", "notice-owner@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = owner.id,
                        celebrantNickname = "오픈됨",
                        startedAt = now.minusDays(1).toLocalDate().atStartOfDay(),
                    ),
                    now.minusDays(2),
                )
            saveParticipant(party, user, createdAt = now.minusDays(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data[0].isHost") { value(false) }
                    jsonPath("$.data[0].hostRollingPaperNoticePending") { value(false) }
                }
        }

        @Test
        fun `조기 종료된 실시간 파티의 주최자에게도 안내가 필요하다`() {
            val user = saveUser("kakao-notice-live", "notice-live@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val liveStartAt = now.minusMinutes(5)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        celebrantNickname = "조기종료",
                        startedAt = liveStartAt,
                    ).apply {
                        liveStartedAt = liveStartAt
                        liveEndingStartedAt = now.minusMinutes(3)
                    },
                    now.minusDays(1),
                )
            saveParticipant(party, user, createdAt = now.minusHours(1))

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data[0].hostRollingPaperNoticePending") { value(true) }
                }
        }

        @Test
        fun `확인 API 호출 후에는 안내가 필요하지 않다`() {
            val user = saveUser("kakao-notice-seen", "notice-seen@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        celebrantNickname = "확인함",
                        startedAt = now.minusDays(1).toLocalDate().atStartOfDay(),
                    ),
                    now.minusDays(2),
                )
            saveParticipant(party, user, createdAt = now.minusDays(1))

            mockMvc
                .post("/api/v1/parties/${party.id}/host-rolling-paper-seen") {
                    header("Authorization", "Bearer $token")
                }.andExpect { status { isOk() } }

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data[0].hostRollingPaperNoticePending") { value(false) }
                }
        }

        private fun saveParty(
            party: Party,
            createdAt: LocalDateTime,
        ): Party {
            val saved = partyRepository.saveAndFlush(party)
            saved.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS)
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

        private fun saveParticipant(
            party: Party,
            user: User,
            hasWrittenPaper: Boolean = false,
            createdAt: LocalDateTime,
        ): Participant {
            val saved =
                participantRepository.saveAndFlush(
                    Participant(
                        party = party,
                        user = user,
                        hasWrittenPaper = hasWrittenPaper,
                    ),
                )
            saved.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS)
            return participantRepository.saveAndFlush(saved)
        }

        private fun MockMvcResultMatchersDsl.expectRealtimeParty(
            party: Party,
            liveStartAt: LocalDateTime,
            index: Int,
        ) {
            jsonPath("$.data[$index].partyId") { value(party.id) }
            jsonPath("$.data[$index].inviteToken") { value("upcominglive001") }
            jsonPath("$.data[$index].partyOption") { value("REALTIME") }
            jsonPath("$.data[$index].celebrantNickname") { value("실시간주인공") }
            jsonPath("$.data[$index].partyStartedAt") {
                value(liveStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].partyEndedAt") {
                value(party.endedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].isHost") { value(false) }
            jsonPath("$.data[$index].rollingPaperWritten") { value(true) }
            jsonPath("$.data[$index].hostRollingPaperOpenAt") { value(nullValue()) }
            jsonPath("$.data[$index].hostRollingPaperNoticePending") { value(false) }
            jsonPath("$.data[$index].realtimeSchedule.enterableFrom") {
                value(
                    liveStartAt
                        .minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
            }
            jsonPath("$.data[$index].realtimeSchedule.liveStartAt") {
                value(liveStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].realtimeSchedule.liveStartedAt") { value(nullValue()) }
            jsonPath("$.data[$index].realtimeSchedule.liveEndAt") {
                value(
                    liveStartAt
                        .plusMinutes(RealtimeParty.START_GRACE_MINUTES)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
            }
            jsonPath("$.data[$index].realtimeStatus") { value("ROLLING_PAPER_OPEN") }
            jsonPath("$.data[$index].realtimeEnterable") { value(false) }
        }

        private fun MockMvcResultMatchersDsl.expectPaperOnlyParty(
            party: Party,
            index: Int,
        ) {
            jsonPath("$.data[$index].partyId") { value(party.id) }
            jsonPath("$.data[$index].inviteToken") { value("upcomingpaper01") }
            jsonPath("$.data[$index].partyOption") { value("PAPER_ONLY") }
            jsonPath("$.data[$index].celebrantNickname") { value(party.celebrantNickname) }
            jsonPath("$.data[$index].partyStartedAt") {
                value(party.startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].partyEndedAt") {
                value(party.endedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].isHost") { value(true) }
            jsonPath("$.data[$index].rollingPaperWritten") { value(false) }
            jsonPath("$.data[$index].hostRollingPaperOpenAt") {
                value(party.hostViewableAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[$index].hostRollingPaperNoticePending") { value(false) }
            jsonPath("$.data[$index].realtimeSchedule") { value(nullValue()) }
            jsonPath("$.data[$index].realtimeStatus") { value(nullValue()) }
            jsonPath("$.data[$index].realtimeEnterable") { value(false) }
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
