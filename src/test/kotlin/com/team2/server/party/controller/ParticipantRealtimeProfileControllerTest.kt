package com.team2.server.party.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ParticipantRealtimeProfileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private val objectMapper = ObjectMapper()

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `GET 회원 첫 진입은 participant를 생성하고 nickname null character null로 응답한다`() {
            val user = saveUser("kakao-get-first", "first@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "getfirst0000001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getfirst0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participantId") { exists() }
                    jsonPath("$.data.isHost") { value(false) }
                    jsonPath("$.data.nickname") { value(nullValue()) }
                    jsonPath("$.data.nicknameEditable") { value(true) }
                    jsonPath("$.data.character") { value(nullValue()) }
                }

            val participant = assertNotNull(participantRepository.findByPartyAndUser(party, user))
            assertEquals(false, participant.isCelebrant)
            assertNull(profileRepository.findByParticipant(participant))
        }

        @Test
        fun `GET 주최자 진입은 isHost true, nicknameEditable false, prefilled nickname과 character`() {
            val host = saveUser("kakao-get-host", "host@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character),
            )
            saveInvite(party, "gethost00000001")
            val accessToken = tokenProvider.issue(host)

            mockMvc
                .get("/api/v1/party-invites/gethost00000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participantId") { value(participant.id) }
                    jsonPath("$.data.isHost") { value(true) }
                    jsonPath("$.data.nickname") { value("홍길동") }
                    jsonPath("$.data.nicknameEditable") { value(false) }
                    jsonPath("$.data.character.characterId") { value(character.id) }
                    jsonPath("$.data.character.name") { value("기본") }
                }
        }

        @Test
        fun `GET 만료된 초대 토큰이면 INVITE_LINK_EXPIRED`() {
            val user = saveUser("kakao-get-expired", "getexp@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "getexpired00001", LocalDateTime.now().minusHours(1))
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getexpired00001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVITE_LINK_EXPIRED") }
                }
            assertEquals(0, participantRepository.count())
        }

        @Test
        fun `GET 종료된 파티이면 PARTY_ENDED`() {
            val user = saveUser("kakao-get-ended", "getend@kakao.local")
            val createdAt = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "getended0000001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getended0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `GET PAPER_ONLY 파티이면 PARTY_NOT_REALTIME`() {
            val user = saveUser("kakao-get-paper", "getpaper@kakao.local")
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "getpaperonly001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getpaperonly001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
                }
        }

        @Test
        fun `GET 존재하지 않는 토큰이면 PARTY_NOT_FOUND`() {
            val user = saveUser("kakao-get-404", "get404@kakao.local")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/missingtoken000/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `GET 인증 없으면 401`() {
            mockMvc
                .get("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile")
                .andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
                }
        }

        @Test
        fun `PUT 회원 첫 작성은 프로필을 생성하고 200으로 응답한다`() {
            val user = saveUser("kakao-put-first", "putfirst@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putfirst0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕용가리", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putfirst0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.isHost") { value(false) }
                    jsonPath("$.data.nickname") { value("안녕용가리") }
                    jsonPath("$.data.nicknameEditable") { value(true) }
                    jsonPath("$.data.character.characterId") { value(character.id) }
                }

            val participant = assertNotNull(participantRepository.findByPartyAndUser(party, user))
            val profile = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("안녕용가리", profile.nickname)
            assertEquals(character.id, profile.character?.id)
        }

        @Test
        fun `PUT 회원 수정은 nickname과 character 모두 갱신한다`() {
            val user = saveUser("kakao-put-update", "putupdate@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = user, isCelebrant = false),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "old", character = character1),
            )
            saveInvite(party, "putupdate000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "new", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/putupdate000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.nickname") { value("new") }
                    jsonPath("$.data.character.characterId") { value(character2.id) }
                }

            val updated = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("new", updated.nickname)
            assertEquals(character2.id, updated.character?.id)
        }

        @Test
        fun `PUT 주최자가 다른 nickname을 보내면 PARTY_HOST_NICKNAME_NOT_EDITABLE`() {
            val host = saveUser("kakao-put-host-x", "puthostx@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character1),
            )
            saveInvite(party, "puthostxnick001")
            val accessToken = tokenProvider.issue(host)
            val body = mapOf("nickname" to "다른이름", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/puthostxnick001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_HOST_NICKNAME_NOT_EDITABLE") }
                }

            val unchanged = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("홍길동", unchanged.nickname)
            assertEquals(character1.id, unchanged.character?.id)
        }

        @Test
        fun `PUT 주최자가 같은 nickname과 다른 character를 보내면 character만 갱신된다`() {
            val host = saveUser("kakao-put-host-ok", "puthostok@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character1),
            )
            saveInvite(party, "puthostokchr001")
            val accessToken = tokenProvider.issue(host)
            val body = mapOf("nickname" to "홍길동", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/puthostokchr001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.isHost") { value(true) }
                    jsonPath("$.data.nickname") { value("홍길동") }
                    jsonPath("$.data.character.characterId") { value(character2.id) }
                }

            val updated = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("홍길동", updated.nickname)
            assertEquals(character2.id, updated.character?.id)
        }

        @Test
        fun `PUT nickname이 blank이면 400`() {
            val user = saveUser("kakao-put-blank", "putblank@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putblank0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "   ", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putblank0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT nickname이 10자 초과면 400`() {
            val user = saveUser("kakao-put-long", "putlong@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putlong00000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "12345678901", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putlong00000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT characterId가 누락되면 400`() {
            val user = saveUser("kakao-put-noc", "putnoc@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putnoc000000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕")

            mockMvc
                .put("/api/v1/party-invites/putnoc000000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT 없는 characterId면 CHARACTER_NOT_FOUND`() {
            val user = saveUser("kakao-put-nochar", "putnochar@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putnochar000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to 999999L)

            mockMvc
                .put("/api/v1/party-invites/putnochar000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("CHARACTER_NOT_FOUND") }
                }
        }

        @Test
        fun `PUT 만료된 토큰이면 INVITE_LINK_EXPIRED, 데이터 변경 없음`() {
            val user = saveUser("kakao-put-exp", "putexp@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putexpired00001", LocalDateTime.now().minusHours(1))
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putexpired00001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVITE_LINK_EXPIRED") }
                }

            assertEquals(0, participantRepository.count())
            assertEquals(0, profileRepository.count())
        }

        @Test
        fun `PUT 종료된 파티이면 PARTY_ENDED`() {
            val user = saveUser("kakao-put-end", "putend@kakao.local")
            val character = saveCharacter("기본")
            val createdAt = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "putended0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putended0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `PUT PAPER_ONLY 파티이면 PARTY_NOT_REALTIME`() {
            val user = saveUser("kakao-put-pp", "putpp@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "putpaperonly001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putpaperonly001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
                }
        }

        @Test
        fun `PUT 인증 없으면 401`() {
            val body = mapOf("nickname" to "안녕", "characterId" to 1L)
            mockMvc
                .put("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
                }
        }

        @Test
        fun `PUT 잘못된 Bearer 토큰이면 AUTH_INVALID_TOKEN`() {
            val body = mapOf("nickname" to "안녕", "characterId" to 1L)
            mockMvc
                .put("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer not-a-jwt")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "tester-$providerId",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )

        private fun saveParty(party: Party): Party = partyRepository.saveAndFlush(party)

        private fun saveInvite(
            party: Party,
            token: String,
            expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        ): PartyInvite =
            partyInviteRepository.saveAndFlush(
                PartyInvite(party = party, token = token, expiresAt = expiresAt),
            )

        private fun saveCharacter(name: String): Character = characterRepository.saveAndFlush(Character(name = name))
    }
