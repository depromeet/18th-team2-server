package com.team2.server.rollingpaper.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class RollingPaperListControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val imageRepository: ImageRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            clearDatabase()
        }

        @AfterEach
        fun tearDown() {
            clearDatabase()
        }

        @Test
        fun `참가자용 목록은 인증 없이 최신순 7개씩 조회된다`() {
            val party = saveRealtimeParty(startedAt = DEFAULT_NOW.withHour(22).withMinute(0))
            val invite = saveInvite(party, "listtoken000001")
            val wrapper = saveWrapperWithImages()
            (1..8).forEach { index ->
                saveRollingPaper(
                    party = party,
                    wrapper = wrapper,
                    writerNickname = "작성자$index",
                    createdAt = DEFAULT_NOW.plusMinutes(index.toLong()),
                )
            }

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "1")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyOption") { value("REALTIME") }
                    jsonPath("$.data.liveEndAt") { value("2026-05-06T22:10:00") }
                    jsonPath("$.data.page") { value(1) }
                    jsonPath("$.data.totalPages") { value(2) }
                    jsonPath("$.data.hasNext") { value(true) }
                    jsonPath("$.data.totalCount") { doesNotExist() }
                    jsonPath("$.data.items.length()") { value(7) }
                    jsonPath("$.data.items[0].writerNickname") { value("작성자8") }
                    jsonPath("$.data.items[0].wrapperImageUrl") { value("/images/rolling-paper-wrappers/first.svg") }
                    jsonPath("$.data.items[6].writerNickname") { value("작성자2") }
                }

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "2")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.page") { value(2) }
                    jsonPath("$.data.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].writerNickname") { value("작성자1") }
                }
        }

        @Test
        fun `참가자용 목록은 유효 Bearer token이 있어도 조회된다`() {
            val user = saveUser("participant-list")
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "authedlist00001")
            val token = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyOption") { value("PAPER_ONLY") }
                    jsonPath("$.data.liveEndAt") { value(nullValue()) }
                }
        }

        @Test
        fun `참가자용 목록은 page가 1보다 작으면 1로 보정하고 빈 목록을 반환한다`() {
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "emptylist000001")

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "0")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.page") { value(1) }
                    jsonPath("$.data.totalPages") { value(0) }
                    jsonPath("$.data.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.totalCount") { doesNotExist() }
                }
        }

        @Test
        fun `참가자용 목록은 파티 자체 종료 후에도 조회된다`() {
            val party = savePaperOnlyParty(createdAt = DEFAULT_NOW.minusDays(8))
            val invite = saveInvite(party, "endedlist000001")

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalPages") { value(0) }
                }
        }

        @Test
        fun `참가자용 목록은 잘못된 Bearer token이면 401`() {
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "invalidlist0001")

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        @Test
        fun `참가자용 목록은 없는 inviteToken이면 404`() {
            mockMvc
                .get("/api/v1/party-invites/missing-token/rolling-papers")
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `주최자용 목록은 소유자만 열람 가능하고 총 개수를 포함한다`() {
            val owner = saveUser("owner-list")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    createdAt = LocalDateTime.of(2026, 5, 5, 14, 30),
                    startedAt =
                        LocalDateTime
                            .now()
                            .minusDays(1)
                            .toLocalDate()
                            .atStartOfDay(),
                )
            val wrapper = saveWrapperWithImages()
            saveRollingPaper(party, wrapper, "축하요정", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.celebrantNickname") { value("홍길동") }
                    jsonPath("$.data.partyEndAt") { value("2026-05-12T14:30:00") }
                    jsonPath("$.data.page") { value(1) }
                    jsonPath("$.data.totalCount") { value(1) }
                    jsonPath("$.data.totalPages") { value(1) }
                    jsonPath("$.data.hasNext") { value(false) }
                    jsonPath("$.data.items[0].writerNickname") { value("축하요정") }
                }
        }

        @Test
        fun `주최자용 목록은 소유자가 아니면 403`() {
            val owner = saveUser("owner-forbidden")
            val other = saveUser("other-forbidden")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt =
                        LocalDateTime
                            .now()
                            .minusDays(1)
                            .toLocalDate()
                            .atStartOfDay(),
                )

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(other)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `주최자용 목록은 열람 가능 전이면 403`() {
            val owner = saveUser("owner-before-open")
            val party =
                saveRealtimeParty(
                    ownerId = owner.id,
                    startedAt = LocalDateTime.now().plusMinutes(1),
                )

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_NOT_VIEWABLE") }
                }
        }

        @Test
        fun `주최자용 목록은 실시간 파티 종료 시각부터 열람 가능하다`() {
            val owner = saveUser("owner-realtime-open")
            val party =
                saveRealtimeParty(
                    ownerId = owner.id,
                    startedAt = LocalDateTime.now().minusMinutes(RealtimeParty.LIVE_DURATION_MINUTES).minusSeconds(1),
                )

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.totalCount") { value(0) }
                    jsonPath("$.data.totalPages") { value(0) }
                }
        }

        @Test
        fun `공통 목록은 초과 페이지 요청값을 유지하고 빈 items를 반환한다`() {
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "overpagelist001")
            val wrapper = saveWrapperWithImages()
            saveRollingPaper(party, wrapper, "하나", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "5")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.page") { value(5) }
                    jsonPath("$.data.totalPages") { value(1) }
                    jsonPath("$.data.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(0) }
                }
        }

        @Test
        fun `wrapper 이미지가 없으면 wrapperImageUrl은 null이다`() {
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "noimagelist0001")
            val wrapper = saveWrapper()
            saveRollingPaper(party, wrapper, "이미지없음", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].wrapperImageUrl") { value(nullValue()) }
                }
        }

        private fun savePaperOnlyParty(
            ownerId: Long = 1L,
            createdAt: LocalDateTime = DEFAULT_NOW.minusDays(1),
            startedAt: LocalDateTime = DEFAULT_NOW.toLocalDate().atStartOfDay(),
        ): Party {
            val saved =
                partyRepository.saveAndFlush(
                    PaperOnlyParty(
                        ownerId = ownerId,
                        celebrantNickname = "홍길동",
                        startedAt = startedAt,
                    ),
                )
            saved.createdAt = createdAt
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveRealtimeParty(
            ownerId: Long = 1L,
            createdAt: LocalDateTime = DEFAULT_NOW.minusDays(1),
            startedAt: LocalDateTime = DEFAULT_NOW.withHour(20).withMinute(0),
        ): Party {
            val saved =
                partyRepository.saveAndFlush(
                    RealtimeParty(
                        ownerId = ownerId,
                        celebrantNickname = "홍길동",
                        startedAt = startedAt,
                    ),
                )
            saved.createdAt = createdAt
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveInvite(
            party: Party,
            token: String,
            expiresAt: LocalDateTime = DEFAULT_NOW.plusDays(1),
        ): PartyInvite =
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    token = token,
                    expiresAt = expiresAt,
                ),
            )

        private fun saveWrapper(): RollingPaperWrapper =
            rollingPaperWrapperRepository.saveAndFlush(RollingPaperWrapper(name = "Topping_Candle"))

        private fun saveWrapperWithImages(): RollingPaperWrapper {
            val wrapper = saveWrapper()
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/second.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = wrapper.id,
                    sortOrder = 1,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/first.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = wrapper.id,
                    sortOrder = 0,
                ),
            )
            return wrapper
        }

        private fun saveRollingPaper(
            party: Party,
            wrapper: RollingPaperWrapper,
            writerNickname: String,
            createdAt: LocalDateTime,
        ): RollingPaper {
            val participant = participantRepository.saveAndFlush(Participant(party = party, hasWrittenPaper = true))
            val saved =
                rollingPaperRepository.saveAndFlush(
                    RollingPaper(
                        wrapper = wrapper,
                        writer = participant,
                        party = party,
                        writerNickname = writerNickname,
                        content = "축하해요",
                    ),
                )
            saved.createdAt = createdAt
            return rollingPaperRepository.saveAndFlush(saved)
        }

        private fun saveUser(providerId: String): User =
            userRepository.saveAndFlush(
                User(
                    name = "사용자",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = "$providerId@kakao.local",
                ),
            )

        private fun clearDatabase() {
            rollingPaperRepository.deleteAll()
            imageRepository.deleteAll()
            rollingPaperWrapperRepository.deleteAll()
            partyInviteRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        companion object {
            private val DEFAULT_NOW: LocalDateTime = LocalDateTime.of(2026, 5, 6, 12, 0)
        }
    }
