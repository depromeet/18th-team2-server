package com.team2.server.rollingpaper.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.common.image.entity.Image
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperTopping
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperToppingRepository
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
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RollingPaperListControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperToppingRepository: RollingPaperToppingRepository,
        private val imageRepository: ImageRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private var defaultOwnerSequence = 0

        @BeforeEach
        fun setUp() {
            clearDatabase()
        }

        @AfterEach
        fun tearDown() {
            clearDatabase()
        }

        @Test
        fun `참가자용 목록은 인증 없이 content와 position 없이 최신순 7개씩 조회된다`() {
            val party = saveRealtimeParty(startedAt = DEFAULT_NOW.withHour(22).withMinute(0)) as RealtimeParty
            party.liveStartedAt = party.startedAt
            partyRepository.saveAndFlush(party)
            val invite = saveInvite(party, "listtoken000001")
            val topping = saveToppingWithImages()
            (1..8).forEach { index ->
                saveRollingPaper(
                    party = party,
                    topping = topping,
                    writerNickname = "작성자$index",
                    createdAt = DEFAULT_NOW.plusMinutes(index.toLong()),
                    content = "축하해요$index",
                )
            }

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "1")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.partyOption") { value("REALTIME") }
                    jsonPath("$.data.liveEndAt") { value("2026-05-06T22:10:00") }
                    jsonPath("$.data.pageInfo.page") { value(1) }
                    jsonPath("$.data.pageInfo.totalCount") { value(8) }
                    jsonPath("$.data.pageInfo.totalPages") { value(2) }
                    jsonPath("$.data.pageInfo.hasNext") { value(true) }
                    jsonPath("$.data.items.length()") { value(7) }
                    jsonPath("$.data.items[0].position") { doesNotExist() }
                    jsonPath("$.data.items[0].writerNickname") { value("작성자8") }
                    jsonPath("$.data.items[0].content") { doesNotExist() }
                    jsonPath("$.data.items[0].toppingImageUrl") { value("/images/rolling-paper-wrappers/first.svg") }
                    jsonPath("$.data.items[6].position") { doesNotExist() }
                    jsonPath("$.data.items[6].writerNickname") { value("작성자2") }
                    jsonPath("$.data.items[6].content") { doesNotExist() }
                }

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "2")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.pageInfo.page") { value(2) }
                    jsonPath("$.data.pageInfo.totalCount") { value(8) }
                    jsonPath("$.data.pageInfo.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(1) }
                    jsonPath("$.data.items[0].position") { doesNotExist() }
                    jsonPath("$.data.items[0].writerNickname") { value("작성자1") }
                    jsonPath("$.data.items[0].content") { doesNotExist() }
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
                    jsonPath("$.data.pageInfo.page") { value(1) }
                    jsonPath("$.data.pageInfo.totalPages") { value(0) }
                    jsonPath("$.data.pageInfo.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(0) }
                    jsonPath("$.data.pageInfo.totalCount") { value(0) }
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
                    jsonPath("$.data.pageInfo.totalPages") { value(0) }
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
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            saveRollingPaper(party, topping, "축하요정", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.celebrantNickname") { value("홍길동") }
                    jsonPath("$.data.partyEndAt") { value("2020-01-08T00:00:00") }
                    jsonPath("$.data.pageInfo.page") { value(1) }
                    jsonPath("$.data.pageInfo.totalCount") { value(1) }
                    jsonPath("$.data.pageInfo.totalPages") { value(1) }
                    jsonPath("$.data.pageInfo.hasNext") { value(false) }
                    jsonPath("$.data.items[0].position") { value(1) }
                    jsonPath("$.data.items[0].writerNickname") { value("축하요정") }
                    jsonPath("$.data.items[0].content") { value("축하해요") }
                }
        }

        @Test
        fun `주최자용 목록은 소유자가 아니면 403`() {
            val owner = saveUser("owner-forbidden")
            val other = saveUser("other-forbidden")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
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
                    startedAt = NOT_VIEWABLE_REALTIME_STARTED_AT,
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
                    startedAt = ALWAYS_VIEWABLE_REALTIME_STARTED_AT,
                )

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.pageInfo.totalCount") { value(0) }
                    jsonPath("$.data.pageInfo.totalPages") { value(0) }
                }
        }

        @Test
        fun `주최자용 상세는 내용과 최신순 기준 순번을 조회한다`() {
            val owner = saveUser("owner-detail")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            saveRollingPaper(party, topping, "첫번째", DEFAULT_NOW.plusMinutes(1), content = "첫 번째 축하")
            val target =
                saveRollingPaper(party, topping, "두번째", DEFAULT_NOW.plusMinutes(2), content = "두 번째 축하")
            saveRollingPaper(party, topping, "세번째", DEFAULT_NOW.plusMinutes(3), content = "세 번째 축하")

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${target.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.rollingPaperId") { value(target.id) }
                    jsonPath("$.data.content") { value("두 번째 축하") }
                    jsonPath("$.data.writerNickname") { value("두번째") }
                    jsonPath("$.data.position") { value(2) }
                    jsonPath("$.data.totalCount") { value(3) }
                }
        }

        @Test
        fun `주최자용 상세는 같은 생성 시각이면 id 내림차순 기준 순번을 계산한다`() {
            val owner = saveUser("owner-detail-same-time")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            val old = saveRollingPaper(party, topping, "먼저작성", DEFAULT_NOW, content = "먼저")
            val latest = saveRollingPaper(party, topping, "나중작성", DEFAULT_NOW, content = "나중")

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${old.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.position") { value(2) }
                    jsonPath("$.data.totalCount") { value(2) }
                }

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${latest.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.position") { value(1) }
                    jsonPath("$.data.totalCount") { value(2) }
                }
        }

        @Test
        fun `주최자용 상세는 해당 파티의 롤링페이퍼가 아니면 404`() {
            val owner = saveUser("owner-detail-missing")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val otherParty =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            val otherRollingPaper = saveRollingPaper(otherParty, topping, "다른파티", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${otherRollingPaper.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_NOT_FOUND") }
                }
        }

        @Test
        fun `주최자용 상세는 소유자가 아니면 403`() {
            val owner = saveUser("owner-detail-forbidden")
            val other = saveUser("other-detail-forbidden")
            val party =
                savePaperOnlyParty(
                    ownerId = owner.id,
                    startedAt = ALWAYS_VIEWABLE_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            val rollingPaper = saveRollingPaper(party, topping, "작성자", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${rollingPaper.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(other)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
                }
        }

        @Test
        fun `주최자용 상세는 열람 가능 전이면 403`() {
            val owner = saveUser("owner-detail-before-open")
            val party =
                saveRealtimeParty(
                    ownerId = owner.id,
                    startedAt = NOT_VIEWABLE_REALTIME_STARTED_AT,
                )
            val topping = saveToppingWithImages()
            val rollingPaper = saveRollingPaper(party, topping, "작성자", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/parties/${party.id}/rolling-papers/${rollingPaper.id}") {
                    header("Authorization", "Bearer ${tokenProvider.issue(owner)}")
                }.andExpect {
                    status { isForbidden() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_NOT_VIEWABLE") }
                }
        }

        @Test
        fun `공통 목록은 초과 페이지 요청값을 유지하고 빈 items를 반환한다`() {
            val party = savePaperOnlyParty()
            val invite = saveInvite(party, "overpagelist001")
            val topping = saveToppingWithImages()
            saveRollingPaper(party, topping, "하나", DEFAULT_NOW)

            mockMvc
                .get("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    param("page", "5")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.pageInfo.page") { value(5) }
                    jsonPath("$.data.pageInfo.totalPages") { value(1) }
                    jsonPath("$.data.pageInfo.hasNext") { value(false) }
                    jsonPath("$.data.items.length()") { value(0) }
                }
        }

        private fun savePaperOnlyParty(
            ownerId: Long? = null,
            createdAt: LocalDateTime = DEFAULT_NOW.minusDays(1),
            startedAt: LocalDateTime = DEFAULT_NOW.toLocalDate().atStartOfDay(),
        ): Party {
            val actualOwnerId = ownerId ?: saveDefaultOwner().id
            val saved =
                partyRepository.saveAndFlush(
                    PaperOnlyParty(
                        ownerId = actualOwnerId,
                        celebrantNickname = "홍길동",
                        startedAt = startedAt,
                    ),
                )
            saved.createdAt = createdAt
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveRealtimeParty(
            ownerId: Long? = null,
            createdAt: LocalDateTime = DEFAULT_NOW.minusDays(1),
            startedAt: LocalDateTime = DEFAULT_NOW.withHour(20).withMinute(0),
        ): Party {
            val actualOwnerId = ownerId ?: saveDefaultOwner().id
            val saved =
                partyRepository.saveAndFlush(
                    RealtimeParty(
                        ownerId = actualOwnerId,
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

        private fun saveTopping(): RollingPaperTopping =
            rollingPaperToppingRepository.saveAndFlush(RollingPaperTopping(name = "Topping_Candle"))

        private fun saveToppingWithImages(): RollingPaperTopping {
            val topping = saveTopping()
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/second.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping.id,
                    sortOrder = 1,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/first.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping.id,
                    sortOrder = 0,
                ),
            )
            return topping
        }

        private fun saveRollingPaper(
            party: Party,
            topping: RollingPaperTopping,
            writerNickname: String,
            createdAt: LocalDateTime,
            content: String = "축하해요",
        ): RollingPaper {
            val participant = participantRepository.saveAndFlush(Participant(party = party, hasWrittenPaper = true))
            val saved =
                rollingPaperRepository.saveAndFlush(
                    RollingPaper(
                        topping = topping,
                        writer = participant,
                        party = party,
                        writerNickname = writerNickname,
                        content = content,
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

        private fun saveDefaultOwner(): User {
            defaultOwnerSequence += 1
            return saveUser("default-owner-$defaultOwnerSequence")
        }

        private fun clearDatabase() {
            databaseCleanup.execute()
        }

        companion object {
            private val DEFAULT_NOW: LocalDateTime = LocalDateTime.of(2026, 5, 6, 12, 0)
            private val ALWAYS_VIEWABLE_STARTED_AT: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0)
            private val ALWAYS_VIEWABLE_REALTIME_STARTED_AT: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0)
            private val NOT_VIEWABLE_REALTIME_STARTED_AT: LocalDateTime = LocalDateTime.of(2099, 1, 1, 0, 0)
        }
    }
