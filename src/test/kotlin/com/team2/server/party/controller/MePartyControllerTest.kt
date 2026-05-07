package com.team2.server.party.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
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
import org.springframework.test.web.servlet.MockMvcResultMatchersDsl
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
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
        fun `내가 참여 중이고 종료되지 않은 파티 목록을 조회한다`() {
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

            participantRepository.save(Participant(party = realtimeParty, user = user, hasWrittenPaper = true))
            participantRepository.save(Participant(party = paperOnlyParty, user = user))
            participantRepository.save(Participant(party = endedParty, user = user))
            participantRepository.save(Participant(party = otherParty, user = other))
            saveInvite(realtimeParty, "upcominglive001")
            saveInvite(paperOnlyParty, "upcomingpaper01")

            mockMvc
                .get("/api/v1/me/upcoming-parties") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.length()") { value(2) }
                    expectRealtimeParty(realtimeParty, liveStartAt)
                    expectPaperOnlyParty(paperOnlyParty)
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
            participantRepository.save(Participant(party = party, user = user))
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

        private fun MockMvcResultMatchersDsl.expectRealtimeParty(
            party: Party,
            liveStartAt: LocalDateTime,
        ) {
            jsonPath("$.data[0].partyId") { value(party.id) }
            jsonPath("$.data[0].inviteToken") { value("upcominglive001") }
            jsonPath("$.data[0].partyOption") { value("REALTIME") }
            jsonPath("$.data[0].celebrantNickname") { value("실시간주인공") }
            jsonPath("$.data[0].partyStartedAt") {
                value(liveStartAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[0].partyEndedAt") {
                value(party.endedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[0].isHost") { value(false) }
            jsonPath("$.data[0].rollingPaperWritten") { value(true) }
            jsonPath("$.data[0].hostRollingPaperOpenAt") { value(nullValue()) }
            jsonPath("$.data[0].realtimeSchedule.enterableFrom") {
                value(
                    liveStartAt
                        .minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
            }
            jsonPath("$.data[0].realtimeSchedule.liveStartAt") {
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

        private fun MockMvcResultMatchersDsl.expectPaperOnlyParty(party: Party) {
            jsonPath("$.data[1].partyId") { value(party.id) }
            jsonPath("$.data[1].inviteToken") { value("upcomingpaper01") }
            jsonPath("$.data[1].partyOption") { value("PAPER_ONLY") }
            jsonPath("$.data[1].isHost") { value(true) }
            jsonPath("$.data[1].rollingPaperWritten") { value(false) }
            jsonPath("$.data[1].hostRollingPaperOpenAt") {
                value(party.hostViewableAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            jsonPath("$.data[1].realtimeSchedule") { value(nullValue()) }
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
