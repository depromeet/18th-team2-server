package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperTopping
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperToppingRepository
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
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ArchivePartyDetailControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val rollingPaperRepository: RollingPaperRepository,
        private val toppingRepository: RollingPaperToppingRepository,
        private val chatMessageRepository: ChatMessageRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `REALTIME 파티 참가자가 본인이 작성한 경우 상세 응답을 반환한다`() {
            val owner = saveUser("kakao-owner-1", "owner1@x")
            val me = saveUser("kakao-me-1", "me1@x")
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        name = "김유빈의 파티",
                        celebrantNickname = "김유빈",
                        startedAt = LocalDateTime.now().minusDays(1),
                    ),
                )
            val ownerParticipant = participantRepository.save(Participant(party = party, user = owner))
            val myParticipant = participantRepository.save(Participant(party = party, user = me))
            profileRepository.save(RealtimeParticipantProfile(participant = ownerParticipant, nickname = "주최자"))
            profileRepository.save(RealtimeParticipantProfile(participant = myParticipant, nickname = "해파리"))
            val topping = toppingRepository.save(RollingPaperTopping(name = "Topping_Candle"))
            rollingPaperRepository.save(
                RollingPaper(
                    topping = topping,
                    writer = myParticipant,
                    party = party,
                    writerNickname = "해파리",
                    content = "축하해",
                ),
            )

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(me)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyId") { value(party.id) }
                    jsonPath("$.data.celebrantNickname") { value("김유빈") }
                    jsonPath("$.data.partyOption") { value("REALTIME") }
                    jsonPath("$.data.role") { value("PARTICIPANT") }
                    jsonPath("$.data.participantCount") { value(2) }
                    jsonPath("$.data.paperCount") { value(1) }
                    jsonPath("$.data.participants[0].nickname") { value("주최자") }
                    jsonPath("$.data.participants[1].nickname") { value("해파리") }
                    jsonPath("$.data.chatMessages.length()") { value(0) }
                    jsonPath("$.data.chatHasMore") { value(false) }
                    jsonPath("$.data.myPaperWritten") { value(true) }
                    jsonPath("$.data.myPaperContent") { value("축하해") }
                    jsonPath("$.data.myPaperWriterNickname") { value("해파리") }
                }
        }

        @Test
        fun `REALTIME 파티 주최자는 role이 HOST이고 본인 미작성이면 myPaper 필드는 null이다`() {
            val owner = saveUser("kakao-owner-2", "owner2@x")
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        name = "p",
                        celebrantNickname = "홍",
                        startedAt = LocalDateTime.now(),
                    ),
                )
            participantRepository.save(Participant(party = party, user = owner))

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.role") { value("HOST") }
                    jsonPath("$.data.myPaperWritten") { value(false) }
                    jsonPath("$.data.myPaperContent") { value(nullValue()) }
                    jsonPath("$.data.myPaperWriterNickname") { value(nullValue()) }
                    jsonPath("$.data.myPaperToppingImageUrl") { value(nullValue()) }
                }
        }

        @Test
        fun `PAPER_ONLY 파티는 participants와 chatMessages가 빈 배열이다`() {
            val me = saveUser("kakao-paper-me", "paper-me@x")
            val party =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = 99L,
                        name = "김유빈의 롤링페이퍼",
                        celebrantNickname = "김유빈",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                )
            participantRepository.save(Participant(party = party, user = me))

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(me)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyOption") { value("PAPER_ONLY") }
                    jsonPath("$.data.participants.length()") { value(0) }
                    jsonPath("$.data.participantCount") { value(0) }
                    jsonPath("$.data.chatMessages.length()") { value(0) }
                    jsonPath("$.data.chatHasMore") { value(false) }
                }
        }

        @Test
        fun `채팅 60개면 최근 50개와 chatHasMore true를 반환한다`() {
            val me = saveUser("kakao-chat-me", "chat-me@x")
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 99L,
                        name = "p",
                        celebrantNickname = "홍",
                        startedAt = LocalDateTime.now().minusDays(1),
                    ),
                )
            val myParticipant = participantRepository.save(Participant(party = party, user = me))
            val profile =
                profileRepository.save(RealtimeParticipantProfile(participant = myParticipant, nickname = "해파리"))
            repeat(60) { idx ->
                chatMessageRepository.save(ChatMessage(content = "m$idx", party = party, profile = profile))
            }

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(me)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.chatMessages.length()") { value(50) }
                    jsonPath("$.data.chatHasMore") { value(true) }
                    jsonPath("$.data.chatMessages[0].content") { value("m59") }
                    jsonPath("$.data.chatMessages[0].authorName") { value("해파리") }
                }
        }

        @Test
        fun `Authorization 헤더가 없으면 401을 반환한다`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )

            mockMvc.get("/api/v1/archive/party/${party.id}").andExpect {
                status { isUnauthorized() }
            }
        }

        @Test
        fun `잘못된 Bearer 토큰이면 401을 반환한다`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `해당 파티 participant가 아니면 403 PARTY_FORBIDDEN을 반환한다`() {
            val outsider = saveUser("kakao-outsider", "outsider@x")
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 999L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )

            mockMvc
                .get("/api/v1/archive/party/${party.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(outsider)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `없는 partyId면 404 PARTY_NOT_FOUND를 반환한다`() {
            val me = saveUser("kakao-not-found", "notfound@x")

            mockMvc
                .get("/api/v1/archive/party/99999") {
                    header("Authorization", "Bearer ${tokenProvider.issue(me)}")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "유저",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
