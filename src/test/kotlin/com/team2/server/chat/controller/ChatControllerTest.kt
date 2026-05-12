package com.team2.server.chat.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
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
import org.springframework.test.web.servlet.MvcResult
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

        // ── POST /api/v1/party-invites/{inviteToken}/realtime-participants/stream ──

        @Test
        fun `비로그인 사용자 입장 + 구독 성공`() {
            val character = characterRepository.save(Character(name = "cat"))
            val (_, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            enterAndSubscribe(invite.token, """{"nickname": "홍길동", "characterId": ${character.id}}""")
        }

        @Test
        fun `존재하지 않는 초대 토큰으로 입장 시 404`() {
            val character = characterRepository.save(Character(name = "dog"))

            mockMvc
                .post("/api/v1/party-invites/nonexistent0001/realtime-participants/stream") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "홍길동", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isNotFound() }
                }
        }

        @Test
        fun `PAPER_ONLY 파티 입장 시 400`() {
            val owner = saveUser("paper-owner-${System.nanoTime()}", "paper-${System.nanoTime()}@test.local")
            val character = characterRepository.save(Character(name = "bird"))
            val party =
                partyRepository.save(
                    com.team2.server.party.domain.entity.PaperOnlyParty(
                        ownerId = owner.id,
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
                .post("/api/v1/party-invites/${invite.token}/realtime-participants/stream") {
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

            enterAndSubscribe(
                invite.token,
                """{"nickname": "테스터", "characterId": ${character.id}}""",
                token,
            )

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
                enterAndSubscribe(invite.token, """{"nickname": "손님", "characterId": ${character.id}}""")

            val participantToken = parseSseEnteredToken(enterResult.response.contentAsString)

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
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().plusMinutes(3))

            val enterResult =
                enterAndSubscribe(invite.token, """{"nickname": "손님2", "characterId": ${character.id}}""")

            val participantToken = parseSseEnteredToken(enterResult.response.contentAsString)

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

        @Test
        fun `입장 시 이전 메시지가 entered 이벤트에 포함`() {
            val character = characterRepository.save(Character(name = "cat2"))
            val (party, invite) = savePartyWithInvite(LocalDateTime.now().minusMinutes(5))

            val firstEnterResult =
                enterAndSubscribe(invite.token, """{"nickname": "먼저온사람", "characterId": ${character.id}}""")

            val firstToken = parseSseEnteredToken(firstEnterResult.response.contentAsString)
            val profile = profileRepository.findByParticipantToken(firstToken)!!

            chatMessageRepository.save(
                com.team2.server.chat.entity.ChatMessage(
                    content = "먼저 보낸 메시지",
                    party = party,
                    profile = profile,
                ),
            )

            val character2 = characterRepository.save(Character(name = "dog2"))
            val secondEnterResult =
                enterAndSubscribe(invite.token, """{"nickname": "나중에온사람", "characterId": ${character2.id}}""")

            val body = secondEnterResult.response.getContentAsString(Charsets.UTF_8)
            val enteredJson = parseSseEventData(body, "entered")
            val messages = objectMapper.readTree(enteredJson)["messages"]
            assert(messages.isArray && messages.size() == 1)
            assert(messages[0]["content"].asText() == "먼저 보낸 메시지")
        }

        // ── Helpers ──

        private fun enterAndSubscribe(
            inviteToken: String,
            requestBody: String,
            jwtToken: String? = null,
        ): MvcResult =
            mockMvc
                .post("/api/v1/party-invites/$inviteToken/realtime-participants/stream") {
                    contentType = MediaType.APPLICATION_JSON
                    accept = MediaType.TEXT_EVENT_STREAM
                    content = requestBody
                    jwtToken?.let { header("Authorization", "Bearer $it") }
                }.andExpect {
                    status { isOk() }
                    request { asyncStarted() }
                    header { string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")) }
                }.andReturn()

        private fun parseSseEventData(
            sseBody: String,
            eventName: String,
        ): String {
            val lines = sseBody.lines()
            var foundEvent = false
            for (line in lines) {
                if (line == "event:$eventName") {
                    foundEvent = true
                    continue
                }
                if (foundEvent && line.startsWith("data:")) return line.removePrefix("data:")
            }
            throw IllegalStateException("SSE event '$eventName' not found in: $sseBody")
        }

        private fun parseSseEnteredToken(sseBody: String): String =
            objectMapper.readTree(parseSseEventData(sseBody, "entered"))["participantToken"].asText()

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
            val owner = saveUser("party-owner-${System.nanoTime()}", "owner-${System.nanoTime()}@test.local")
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
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
