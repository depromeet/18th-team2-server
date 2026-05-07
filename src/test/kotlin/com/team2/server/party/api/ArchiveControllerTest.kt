package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.RealtimeParty
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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

        @Test
        fun `REALTIME 파티는 type PARTY로 매핑된다`() {
            val user = saveUser("kakao-archive-party", "archive-party@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "김루카 생일 파티",
                        celebrantNickname = "김루카",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            val participant = saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].id") { value(participant.id.toString()) }
                    jsonPath("$.data.items[0].type") { value("PARTY") }
                    jsonPath("$.data.items[0].title") { value("김루카 생일 파티") }
                    jsonPath("$.data.items[0].date") {
                        value(
                            party
                                .endedAt()
                                .atOffset(ZoneOffset.ofHours(9))
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        )
                    }
                    jsonPath("$.data.totalCount") { value(1) }
                }
        }

        @Test
        fun `PAPER_ONLY 파티는 type PAPER로 매핑된다`() {
            val user = saveUser("kakao-archive-paper", "archive-paper@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = user.id,
                        name = "축하해요",
                        celebrantNickname = "민수",
                        startedAt = now.toLocalDate().plusDays(1).atStartOfDay(),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].type") { value("PAPER") }
                }
        }

        @Test
        fun `party name이 null이면 title은 빈 문자열로 응답한다`() {
            val user = saveUser("kakao-archive-noname", "archive-noname@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = null,
                        celebrantNickname = "이름없음",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].title") { value("") }
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

        private fun saveParticipant(
            party: Party,
            user: User,
            createdAt: LocalDateTime,
        ): Participant {
            val saved =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = user),
                )
            saved.createdAt = createdAt.truncatedTo(ChronoUnit.SECONDS)
            return participantRepository.saveAndFlush(saved)
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
