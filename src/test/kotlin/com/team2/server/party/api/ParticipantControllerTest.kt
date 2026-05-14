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

        @BeforeEach
        fun setUp() {
            realtimeParticipantProfileRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            imageRepository.deleteAll()
            characterRepository.deleteAll()
            userRepository.deleteAll()

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
            imageRepository.save(
                Image(
                    imageUrl = "https://cdn/char.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character.id,
                    sortOrder = 0,
                ),
            )

            val ownerParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = owner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = ownerParticipant, nickname = "주최자", character = character),
            )
            val memberParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = member, isCelebrant = false))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = memberParticipant, nickname = "참가자A", character = character),
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
                    jsonPath("$.data.participants[0].characterImageUrl") { value("https://cdn/char.png") }
                    jsonPath("$.data.participants[1].joinOrder") { value(2) }
                    jsonPath("$.data.participants[1].nickname") { value("참가자A") }
                    jsonPath("$.data.participants[1].isOwner") { value(false) }
                    jsonPath("$.data.participants[1].isCelebrant") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(false) }
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
        fun `PaperOnly 파티 호출 시 400 PARTY_NOT_REALTIME`() {
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
        fun `존재하지 않는 파티 호출 시 404 PARTY_NOT_FOUND`() {
            val token = tokenProvider.issue(owner)

            mockMvc
                .get("/api/v1/parties/99999/participants") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `Authorization 헤더 없으면 401`() {
            mockMvc
                .get("/api/v1/parties/${realtimeParty.id}/participants")
                .andExpect {
                    status { isUnauthorized() }
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
                    jsonPath("$.data.participants[1].isOwner") { value(false) }
                    jsonPath("$.data.participants[1].isMe") { value(false) }
                }
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
