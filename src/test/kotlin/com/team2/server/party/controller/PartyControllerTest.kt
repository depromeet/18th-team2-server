package com.team2.server.party.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
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

@SpringBootTest
@AutoConfigureMockMvc
class PartyControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val characterRepository: CharacterRepository,
        private val participantRepository: ParticipantRepository,
        private val imageRepository: ImageRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private lateinit var party: Party
        private lateinit var noChatParty: Party
        private lateinit var endedParty: Party
        private lateinit var character: Character

        @BeforeEach
        fun setUp() {
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            characterRepository.deleteAll()
            imageRepository.deleteAll()
            userRepository.deleteAll()

            party =
                partyRepository.save(
                    Party(
                        shareLink = "active-link",
                        name = "생일파티",
                        celebrantNickname = "홍길동",
                        purpose = PartyPurpose.BIRTHDAY,
                        option = PartyOption.REALTIME,
                        startedAt = LocalDateTime.now(),
                        endedAt = LocalDateTime.now().plusDays(7),
                        isChattingAllow = true,
                    ),
                )
            noChatParty =
                partyRepository.save(
                    Party(
                        shareLink = "no-chat-link",
                        name = "페이퍼파티",
                        celebrantNickname = "박영희",
                        purpose = PartyPurpose.BIRTHDAY,
                        option = PartyOption.PAPER_ONLY,
                        startedAt = LocalDateTime.now(),
                        endedAt = LocalDateTime.now().plusDays(7),
                        isChattingAllow = false,
                    ),
                )
            endedParty =
                partyRepository.save(
                    Party(
                        shareLink = "ended-link",
                        name = "종료파티",
                        celebrantNickname = "김철수",
                        purpose = PartyPurpose.BIRTHDAY,
                        option = PartyOption.PAPER_ONLY,
                        startedAt = LocalDateTime.now().minusDays(14),
                        endedAt = LocalDateTime.now().minusDays(1),
                        isChattingAllow = false,
                    ),
                )
	            character = characterRepository.save(Character(name = "곰돌이"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/character1.jpg",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character.id,
                ),
            )
        }

        // --- GET /api/parties/{shareLink} ---

        @Test
        fun `파티 정보 조회 성공`() {
            mockMvc.get("/api/parties/active-link").andExpect {
                status { isOk() }
                jsonPath("$.data.name") { value("생일파티") }
                jsonPath("$.data.celebrantNickname") { value("홍길동") }
                jsonPath("$.data.purpose") { value("BIRTHDAY") }
                jsonPath("$.data.option") { value("REALTIME") }
                jsonPath("$.data.ended") { value(false) }
                jsonPath("$.data.myParticipant") { doesNotExist() }
            }
        }

        @Test
        fun `종료된 파티도 정상 조회되고 ended가 true`() {
            mockMvc.get("/api/parties/ended-link").andExpect {
                status { isOk() }
                jsonPath("$.data.ended") { value(true) }
            }
        }

        @Test
        fun `존재하지 않는 shareLink 조회 시 404`() {
            mockMvc.get("/api/parties/no-such-link").andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
            }
        }

        @Test
        fun `회원이 이미 참여한 파티 조회 시 myParticipant 포함`() {
            val user =
                userRepository.save(
                    User(
                        name = "유저",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "kakao-info-1",
                        email = "info@kakao.local",
                    ),
                )
            val token = tokenProvider.issue(user)
            participantRepository.save(
                com.team2.server.party.entity.Participant(
                    party = party,
                    character = character,
                    user = user,
                    nickname = "기존참여자",
                ),
            )

            mockMvc
                .get("/api/parties/active-link") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.myParticipant.nickname") { value("기존참여자") }
                    jsonPath("$.data.myParticipant.characterImageUrl") { value("/images/characters/character1.jpg") }
                }
        }

        // --- POST /api/parties/{shareLink}/participants ---

        @Test
        fun `비회원 파티 참여 성공`() {
            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "비회원", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.nickname") { value("비회원") }
                    jsonPath("$.data.characterImageUrl") { value("/images/characters/character1.jpg") }
                    jsonPath("$.data.participantId") { isNumber() }
                }
        }

        @Test
        fun `회원 파티 참여 성공`() {
            val user =
                userRepository.save(
                    User(
                        name = "회원",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "kakao-join-1",
                        email = "join@kakao.local",
                    ),
                )
            val token = tokenProvider.issue(user)

            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "회원참여", "characterId": ${character.id}}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.nickname") { value("회원참여") }
                    jsonPath("$.data.characterImageUrl") { value("/images/characters/character1.jpg") }
                }
        }

        @Test
        fun `종료된 파티 참여 시 400`() {
            mockMvc
                .post("/api/parties/ended-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "늦참", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `회원 중복 참여 시 409`() {
            val user =
                userRepository.save(
                    User(
                        name = "중복유저",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "kakao-dup-1",
                        email = "dup@kakao.local",
                    ),
                )
            val token = tokenProvider.issue(user)
            participantRepository.save(
                com.team2.server.party.entity.Participant(
                    party = party,
                    character = character,
                    user = user,
                    nickname = "이미참여",
                ),
            )

            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "또참여", "characterId": ${character.id}}"""
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("ALREADY_JOINED") }
                }
        }

        @Test
        fun `닉네임 빈값이면 400`() {
            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `존재하지 않는 characterId면 404`() {
            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "닉네임", "characterId": 99999}"""
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("CHARACTER_NOT_FOUND") }
                }
        }

        @Test
        fun `채팅 허용 파티에서 characterId 없으면 400`() {
            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "닉네임"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("CHARACTER_REQUIRED") }
                }
        }

        @Test
        fun `채팅 비허용 파티에서 characterId 있으면 400`() {
            mockMvc
                .post("/api/parties/no-chat-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "닉네임", "characterId": ${character.id}}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("CHARACTER_NOT_ALLOWED") }
                }
        }

        @Test
        fun `채팅 비허용 파티는 characterId 없이 참여 성공`() {
            mockMvc
                .post("/api/parties/no-chat-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "닉네임"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.nickname") { value("닉네임") }
                    jsonPath("$.data.characterImageUrl") { doesNotExist() }
                }
        }

        @Test
        fun `인증 없이 파티 조회 가능 (permitAll)`() {
            mockMvc.get("/api/parties/active-link").andExpect {
                status { isOk() }
            }
        }

        @Test
        fun `잘못된 토큰으로 파티 조회 시 401`() {
            mockMvc
                .get("/api/parties/active-link") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        @Test
        fun `잘못된 토큰으로 파티 참여 시 401`() {
            mockMvc
                .post("/api/parties/active-link/participants") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname": "닉네임", "characterId": ${character.id}}"""
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }
    }
