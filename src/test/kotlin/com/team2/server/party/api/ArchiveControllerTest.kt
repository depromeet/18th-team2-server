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

        @Test
        fun `host로 만든 파티와 참여한 파티가 함께 조회되고 participant id DESC로 정렬된다`() {
            val user = saveUser("kakao-archive-multi", "archive-multi@kakao.local")
            val other = saveUser("kakao-archive-other", "archive-other@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val hostParty =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "내 파티",
                        celebrantNickname = "본인",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(3),
                )
            val joinedPaperParty =
                saveParty(
                    PaperOnlyParty(
                        ownerId = other.id,
                        name = "친구 파티",
                        celebrantNickname = "친구",
                        startedAt = now.toLocalDate().plusDays(1).atStartOfDay(),
                    ),
                    now.minusHours(2),
                )
            val joinedRealtimeParty =
                saveParty(
                    RealtimeParty(
                        ownerId = other.id,
                        name = "친구 라이브",
                        celebrantNickname = "친구2",
                        startedAt = now.plusHours(4),
                    ),
                    now.minusHours(1),
                )

            // 가입 순서: hostParty 먼저, 그다음 joinedPaperParty, 마지막 joinedRealtimeParty
            // 정렬: participant.id DESC → joinedRealtimeParty, joinedPaperParty, hostParty
            saveParticipant(hostParty, user, now.minusHours(3))
            saveParticipant(joinedPaperParty, user, now.minusHours(2))
            saveParticipant(joinedRealtimeParty, user, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(3) }
                    jsonPath("$.data.items[0].title") { value("친구 라이브") }
                    jsonPath("$.data.items[1].title") { value("친구 파티") }
                    jsonPath("$.data.items[2].title") { value("내 파티") }
                    jsonPath("$.data.totalCount") { value(3) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                }
        }

        @Test
        fun `다른 사용자가 가입한 파티는 보관함에 포함되지 않는다`() {
            val user = saveUser("kakao-archive-isolation", "archive-isolation@kakao.local")
            val other = saveUser("kakao-archive-other2", "archive-other2@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val otherParty =
                saveParty(
                    RealtimeParty(
                        ownerId = other.id,
                        name = "남의 파티",
                        celebrantNickname = "타인",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(otherParty, other, now.minusHours(1))

            mockMvc
                .get("/api/v1/archive") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.totalCount") { value(0) }
                }
        }

        @Test
        fun `size보다 항목이 많으면 nextCursor가 마지막 항목 id로 설정된다`() {
            val user = saveUser("kakao-archive-page1", "archive-page1@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val participants =
                (1..3).map { idx ->
                    val party =
                        saveParty(
                            RealtimeParty(
                                ownerId = user.id,
                                name = "파티 $idx",
                                celebrantNickname = "주인공 $idx",
                                startedAt = now.plusHours(idx.toLong()),
                            ),
                            now.minusHours(idx.toLong()),
                        )
                    saveParticipant(party, user, now.minusMinutes(idx * 10L))
                }

            mockMvc
                .get("/api/v1/archive?size=2") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(2) }
                    jsonPath("$.data.items[0].id") { value(participants[2].id.toString()) }
                    jsonPath("$.data.items[1].id") { value(participants[1].id.toString()) }
                    jsonPath("$.data.nextCursor") { value(participants[1].id.toString()) }
                    jsonPath("$.data.totalCount") { value(3) }
                }
        }

        @Test
        fun `cursor로 다음 페이지를 받으면 남은 항목과 nextCursor null을 응답한다`() {
            val user = saveUser("kakao-archive-page2", "archive-page2@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            val participants =
                (1..3).map { idx ->
                    val party =
                        saveParty(
                            RealtimeParty(
                                ownerId = user.id,
                                name = "파티 $idx",
                                celebrantNickname = "주인공 $idx",
                                startedAt = now.plusHours(idx.toLong()),
                            ),
                            now.minusHours(idx.toLong()),
                        )
                    saveParticipant(party, user, now.minusMinutes(idx * 10L))
                }

            // participants[1].id 이전 항목만 남음 → participants[0]
            mockMvc
                .get("/api/v1/archive?size=2&cursor=${participants[1].id}") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].id") { value(participants[0].id.toString()) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(3) }
                }
        }

        @Test
        fun `항목 수가 정확히 size와 같으면 nextCursor는 null이다`() {
            val user = saveUser("kakao-archive-exact", "archive-exact@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

            (1..2).forEach { idx ->
                val party =
                    saveParty(
                        RealtimeParty(
                            ownerId = user.id,
                            name = "파티 $idx",
                            celebrantNickname = "주인공 $idx",
                            startedAt = now.plusHours(idx.toLong()),
                        ),
                        now.minusHours(idx.toLong()),
                    )
                saveParticipant(party, user, now.minusMinutes(idx * 10L))
            }

            mockMvc
                .get("/api/v1/archive?size=2") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(2) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                }
        }

        @Test
        fun `존재하지 않는 cursor를 보내면 빈 items와 nextCursor null을 응답한다`() {
            val user = saveUser("kakao-archive-largecur", "archive-largecur@kakao.local")
            val token = tokenProvider.issue(user)
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = user.id,
                        name = "유일",
                        celebrantNickname = "주인공",
                        startedAt = now.plusHours(2),
                    ),
                    now.minusHours(1),
                )
            saveParticipant(party, user, now.minusHours(1))

            // cursor < participant.id 인 항목이 없는 매우 작은 cursor (1)
            mockMvc
                .get("/api/v1/archive?cursor=1") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.nextCursor") { value(nullValue()) }
                    jsonPath("$.data.totalCount") { value(1) }
                }
        }

        @Test
        fun `size가 0이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?size=0").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `size가 51이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?size=51").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 0이면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=0").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 음수면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=-1").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
            }
        }

        @Test
        fun `cursor가 숫자가 아니면 400 INVALID_INPUT을 반환한다`() {
            mockMvc.get("/api/v1/archive?cursor=abc").andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
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
