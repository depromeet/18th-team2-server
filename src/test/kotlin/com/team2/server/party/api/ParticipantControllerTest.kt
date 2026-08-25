package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.image.entity.Image
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.hasSize
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
class ParticipantControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
        private val characterRepository: CharacterRepository,
        private val imageRepository: ImageRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        private lateinit var owner: User
        private lateinit var member: User
        private lateinit var outsider: User
        private lateinit var realtimeParty: RealtimeParty
        private lateinit var paperOnlyParty: PaperOnlyParty
        private lateinit var memberProfile: RealtimeParticipantProfile

        @BeforeEach
        fun setUp() {
            clearData()

            owner = saveUser("participant-owner", "owner@e.com")
            member = saveUser("participant-member", "member@e.com")
            outsider = saveUser("participant-outsider", "outsider@e.com")

            realtimeParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "주최자",
                        startedAt = LocalDateTime.now().plusHours(1),
                    ),
                )
            paperOnlyParty =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = owner.id,
                        celebrantNickname = "롤링",
                        startedAt = LocalDateTime.now().plusDays(1),
                    ),
                )

            val character = characterRepository.save(Character(name = "octopus"))
            saveCharacterImage(character.id, "https://cdn/char.png", sortOrder = 0)
            saveCharacterImage(character.id, "https://cdn/char-thumbnail.png", sortOrder = 1)
            saveCharacterImage(character.id, "https://cdn/char-owner.png", sortOrder = 2)

            val ownerParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = owner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = ownerParticipant, nickname = "주최자", character = character),
            )
            val memberParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = member, isCelebrant = false))
            memberProfile =
                realtimeParticipantProfileRepository.save(
                    RealtimeParticipantProfile(
                        participant = memberParticipant,
                        nickname = "참가자A",
                        character = character,
                    ),
                )
        }

        @Test
        fun `주최자 호출 시 200과 입장 순서대로 정렬된 목록을 반환한다`() {
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(2) }
                    jsonPath("$.data.maxCount") { value(14) }
                    jsonPath("$.data.participants", hasSize<Any>(2))
                    jsonPath("$.data.participants[0].joinOrder") { value(1) }
                    jsonPath("$.data.participants[0].nickname") { value("주최자") }
                    jsonPath("$.data.participants[0].isOwner") { value(true) }
                    jsonPath("$.data.participants[0].isCelebrant") { value(true) }
                    jsonPath("$.data.participants[0].isMe") { value(true) }
                    jsonPath("$.data.participants[0].characterImageUrl") { value("https://cdn/char-owner.png") }
                    jsonPath("$.data.participants[0].thumbnailImageUrl") { value("https://cdn/char-thumbnail.png") }
                    jsonPath("$.data.participants[1].joinOrder") { value(2) }
                    jsonPath("$.data.participants[1].nickname") { value("참가자A") }
                    jsonPath("$.data.participants[1].isOwner") { value(false) }
                    jsonPath("$.data.participants[1].isCelebrant") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(false) }
                    jsonPath("$.data.participants[1].characterImageUrl") { value("https://cdn/char.png") }
                    jsonPath("$.data.participants[1].thumbnailImageUrl") { value("https://cdn/char-thumbnail.png") }
                }
        }

        @Test
        fun `일반 참가자 호출 시 본인의 isMe만 true 다`() {
            val token = tokenProvider.issue(member)

            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participants[0].isMe") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(true) }
                }
        }

        @Test
        fun `실시간 연결 여부와 무관하게 입장한 참여자는 전부 목록에 포함된다`() {
            // presence(SSE/WebSocket 연결 상태)는 참가자 목록 판단에 쓰이지 않는다 — 접속이 끊겼다고
            // 참가자가 아닌 게 되지는 않는다. 오직 명시적 퇴장(hasLeft)만 목록에서 제외하는 기준이다.
            val token = tokenProvider.issue(member)

            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(2) }
                    jsonPath("$.data.participants", hasSize<Any>(2))
                }
        }

        @Test
        fun `퇴장한 참여자는 목록에서 제외된다`() {
            val ownerParticipant =
                realtimeParticipantProfileRepository
                    .findByParticipantPartyIdAndParticipantIsCelebrantTrue(
                        realtimeParty.id,
                    )!!
                    .participant
            ownerParticipant.leave()
            participantRepository.save(ownerParticipant)
            val token = tokenProvider.issue(member)

            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(1) }
                    jsonPath("$.data.participants", hasSize<Any>(1))
                    jsonPath("$.data.participants[0].nickname") { value("참가자A") }
                }
        }

        @Test
        fun `X-Participant-Token 헤더로 비로그인 참가자가 조회 시 토큰 owner의 isMe만 true 다`() {
            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("X-Participant-Token", memberProfile.participantToken)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participants[0].isMe") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(true) }
                }
        }

        @Test
        fun `다른 파티의 X-Participant-Token으로 호출 시 403 PARTY_FORBIDDEN`() {
            val otherParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = outsider.id,
                        celebrantNickname = "다른파티",
                        startedAt = LocalDateTime.now().plusDays(1),
                    ),
                )

            mockMvc
                .get("/api/v1/parties/${otherParty.id}/participants") {
                    header("X-Participant-Token", memberProfile.participantToken)
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `존재하지 않는 X-Participant-Token으로 호출 시 403 PARTY_FORBIDDEN`() {
            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("X-Participant-Token", "deadbeef")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `비참가자 호출 시 403 PARTY_FORBIDDEN`() {
            val token = tokenProvider.issue(outsider)

            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `참가자가 PaperOnly 파티 호출 시 400 PARTY_NOT_REALTIME`() {
            participantRepository.save(Participant(party = paperOnlyParty, user = owner, isCelebrant = true))
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/${paperOnlyParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
                }
        }

        @Test
        fun `비참가자가 PaperOnly 파티 호출 시 인가 우선으로 403 PARTY_FORBIDDEN`() {
            val token = tokenProvider.issue(outsider)

            mockMvc
                .get("/api/v1/parties/${paperOnlyParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `존재하지 않는 파티 호출 시 404 PARTY_NOT_FOUND`() {
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/99999/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `Authorization과 X-Participant-Token 모두 없으면 401 UNAUTHORIZED`() {
            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants")
                .andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("UNAUTHORIZED") }
                }
        }

        @Test
        fun `참가자가 주최자 1명뿐이어도 totalCount는 1이고 maxCount는 14다`() {
            val soloOwner = saveUser("participant-solo-owner", "solo-owner@e.com")
            val soloParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = soloOwner.id,
                        celebrantNickname = "혼자",
                        startedAt = LocalDateTime.now().plusHours(2),
                    ),
                )
            val character = characterRepository.save(Character(name = "solo-char"))
            val participant =
                participantRepository.save(Participant(party = soloParty, user = soloOwner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = participant, nickname = "혼자", character = character),
            )
            val token = tokenProvider.issue(soloOwner)

            mockMvc
                .get("/api/v1/parties/${soloParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(1) }
                    jsonPath("$.data.maxCount") { value(14) }
                    jsonPath("$.data.participants", hasSize<Any>(1))
                    jsonPath("$.data.participants[0].joinOrder") { value(1) }
                    jsonPath("$.data.participants[0].isOwner") { value(true) }
                    jsonPath("$.data.participants[0].isMe") { value(true) }
                }
        }

        @Test
        fun `익명 참가자와 캐릭터 없는 프로필도 응답에 포함된다`() {
            val anonymousProfileParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "익명포함",
                        startedAt = LocalDateTime.now().plusHours(3),
                    ),
                )
            val character = characterRepository.save(Character(name = "anon-char"))
            val ownerP =
                participantRepository.save(
                    Participant(party = anonymousProfileParty, user = owner, isCelebrant = true),
                )
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = ownerP, nickname = "주최자", character = character),
            )
            val anonymousP = participantRepository.save(Participant(party = anonymousProfileParty, user = null))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = anonymousP, nickname = "익명", character = null),
            )
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/${anonymousProfileParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(2) }
                    jsonPath("$.data.participants[1].nickname") { value("익명") }
                    jsonPath("$.data.participants[1].characterId") { value(nullValue()) }
                    jsonPath("$.data.participants[1].characterImageUrl") { value(nullValue()) }
                    jsonPath("$.data.participants[1].thumbnailImageUrl") { value(nullValue()) }
                    jsonPath("$.data.participants[1].isOwner") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(false) }
                }
        }

        @Test
        fun `주최자 캐릭터에 꼬깔모자 이미지가 없으면 기본 이미지로 폴백한다`() {
            val fallbackParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "폴백",
                        startedAt = LocalDateTime.now().plusHours(4),
                    ),
                )
            val character = characterRepository.save(Character(name = "fallback-char"))
            imageRepository.save(
                Image(
                    imageUrl = "https://cdn/fallback.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character.id,
                    sortOrder = 0,
                ),
            )
            val ownerP =
                participantRepository.save(Participant(party = fallbackParty, user = owner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = ownerP, nickname = "주최자", character = character),
            )
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/${fallbackParty.id}/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participants[0].isOwner") { value(true) }
                    jsonPath("$.data.participants[0].characterImageUrl") { value("https://cdn/fallback.png") }
                    jsonPath("$.data.participants[0].thumbnailImageUrl") { value(nullValue()) }
                }
        }

        private fun saveCharacterImage(
            characterId: Long,
            imageUrl: String,
            sortOrder: Int,
        ) {
            imageRepository.save(
                Image(
                    imageUrl = imageUrl,
                    targetType = ImageTargetType.CHARACTER,
                    targetId = characterId,
                    sortOrder = sortOrder,
                ),
            )
        }

        private fun clearData() {
            realtimeParticipantProfileRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            imageRepository.deleteAll()
            characterRepository.deleteAll()
            userRepository.deleteAll()
        }

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.save(
                User(
                    name = "사용자",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )
    }
