package com.team2.server.chat.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val chatMessageRepository: ChatMessageRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private val objectMapper = ObjectMapper()

        @BeforeEach
        fun setUp() {
            cleanAll()
        }

        @AfterEach
        fun tearDown() {
            cleanAll()
        }

        private fun cleanAll() {
            chatMessageRepository.deleteAll()
            profileRepository.deleteAll()
            participantRepository.deleteAll()
            partyInviteRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
            characterRepository.deleteAll()
        }

        // ── POST /api/v1/party-invites/{inviteToken}/realtime-participants ──

        @Test
        fun `비로그인 사용자 라이브 입장 성공`() {
            val character = characterRepository.save(Character(name = "cat"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "홍길동", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.participantToken") { isString() }
                    jsonPath("$.data.messages") { isArray() }
                }
        }

        @Test
        fun `존재하지 않는 초대 토큰으로 입장 시 404`() {
            val character = characterRepository.save(Character(name = "dog"))

            mockMvc
                .post("/api/v1/party-invites/nonexistent0001/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "홍길동", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isNotFound() }
                }
        }

        @Test
        fun `PAPER_ONLY 파티 입장 시 400`() {
            val character = characterRepository.save(Character(name = "bird"))
            val party =
                partyRepository.save(
                    com.team2.server.party.entity.PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusHours(1),
                    ),
                )
            val invite =
                partyInviteRepository.save(
                    PartyInvite(
                        party = party,
                        token = "paperonly00001",
                        expiresAt = LocalDateTime.now().plusDays(1),
                    ),
                )

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "홍길동", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        // ── POST /api/v1/parties/{partyId}/chat-messages ──

        @Test
        fun `JWT로 메시지 전송 성공`() {
            val user = saveUser("kakao-msg-1", "msg1@kakao.local")
            val token = tokenProvider.issue(user)
            val character = characterRepository.save(Character(name = "rabbit"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            // enter first to create profile
            mockMvc
                .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "테스터", "characterId": ${character.id}}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isCreated() }
                }

            mockMvc
                .post("/api/v1/parties/${party.id}/chat-messages") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"content": "안녕하세요"}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.content") { value("안녕하세요") }
                    jsonPath("$.data.senderNickname") { value("테스터") }
                }
        }

        @Test
        fun `participantToken으로 메시지 전송 성공`() {
            val character = characterRepository.save(Character(name = "fox"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            val enterResult =
                mockMvc
                    .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"nickname": "손님", "characterId": ${character.id}}"""
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            val participantToken =
                objectMapper
                    .readTree(enterResult.response.contentAsString)["data"]["participantToken"]
                    .asText()

            mockMvc
                .post("/api/v1/parties/${party.id}/chat-messages") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"content": "반갑습니다"}"""
                    header("X-Participant-Token", participantToken)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.content") { value("반갑습니다") }
                    jsonPath("$.data.senderNickname") { value("손님") }
                }
        }

        @Test
        fun `LIVE_OPEN이 아닌 파티에 메시지 전송 시 400`() {
            val character = characterRepository.save(Character(name = "lion"))
            // ROLLING_PAPER_OPEN 상태: startedAt이 3분 후 (입장 가능 범위 내이지만 아직 LIVE_OPEN 아님)
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().plusMinutes(3))

            // ENTERABLE_BEFORE_MINUTES(5분) 이내이므로 입장 가능, 단 LIVE_OPEN이 아니어서 메시지 전송 불가
            val enterResult =
                mockMvc
                    .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"nickname": "손님2", "characterId": ${character.id}}"""
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            val participantToken =
                objectMapper
                    .readTree(enterResult.response.contentAsString)["data"]["participantToken"]
                    .asText()

            mockMvc
                .post("/api/v1/parties/${party.id}/chat-messages") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"content": "안녕"}"""
                    header("X-Participant-Token", participantToken)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `인증 수단 없이 메시지 전송 시 401`() {
            val (party, _) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            mockMvc
                .post("/api/v1/parties/${party.id}/chat-messages") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"content": "안녕"}"""
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        // ── GET /api/v1/parties/{partyId}/chat-messages/stream ──

        @Test
        fun `JWT로 SSE 구독 성공`() {
            val user = saveUser("kakao-sse-1", "sse1@kakao.local")
            val token = tokenProvider.issue(user)
            val character = characterRepository.save(Character(name = "panda"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "SSE테스터", "characterId": ${character.id}}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isCreated() }
                }

            mockMvc
                .get("/api/v1/parties/${party.id}/chat-messages/stream") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    header { string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")) }
                }
        }

        @Test
        fun `participantToken으로 SSE 구독 성공`() {
            val character = characterRepository.save(Character(name = "tiger"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            val enterResult =
                mockMvc
                    .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"nickname": "SSE손님", "characterId": ${character.id}}"""
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

            val participantToken =
                objectMapper
                    .readTree(enterResult.response.contentAsString)["data"]["participantToken"]
                    .asText()

            mockMvc
                .get("/api/v1/parties/${party.id}/chat-messages/stream") {
                    header("X-Participant-Token", participantToken)
                }.andExpect {
                    status { isOk() }
                    header { string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")) }
                }
        }

        @Test
        fun `이전 메시지가 있는 파티에 입장하면 messages에 포함`() {
            val character = characterRepository.save(Character(name = "cat2"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            // 1st enter — profile 생성
            val firstEnter =
                mockMvc
                    .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"nickname": "먼저온사람", "characterId": ${character.id}}"""
                    }.andExpect { status { isCreated() } }
                    .andReturn()

            val firstToken =
                objectMapper
                    .readTree(firstEnter.response.contentAsString)["data"]["participantToken"]
                    .asText()
            val profile = profileRepository.findByParticipantToken(firstToken)!!

            // 메시지 직접 저장
            chatMessageRepository.save(
                com.team2.server.chat.entity.ChatMessage(
                    content = "먼저 보낸 메시지",
                    party = party,
                    profile = profile,
                ),
            )

            // 2nd enter (다른 참여자)
            val character2 = characterRepository.save(Character(name = "dog2"))
            mockMvc
                .post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "나중에온사람", "characterId": ${character2.id}}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.messages") { isArray() }
                    jsonPath("$.data.messages[0].content") { value("먼저 보낸 메시지") }
                    jsonPath("$.data.messages[0].senderNickname") { value("먼저온사람") }
                }
        }

        // ── Helpers ──

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "테스터",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )

        private fun savePartyWithInvite(startedAt: LocalDateTime): Pair<RealtimeParty, PartyInvite> {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = startedAt,
                    ),
                )
            val invite =
                partyInviteRepository.save(
                    PartyInvite(
                        party = party,
                        token =
                            UUID
                                .randomUUID()
                                .toString()
                                .replace("-", "")
                                .take(16),
                        expiresAt = LocalDateTime.now().plusDays(1),
                    ),
                )
            return Pair(party, invite)
        }
    }
