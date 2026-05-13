package com.team2.server.rollingpaper.controller

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class RollingPaperControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            rollingPaperRepository.deleteAll()
            partyInviteRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
            rollingPaperWrapperRepository.deleteAll()
        }

        @Test
        fun `인증 없이 롤링페이퍼 작성 성공`() {
            val party = saveParty()
            val invite = saveInvite(party, "guestwrite000001")
            val wrapper = saveWrapper()

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "생일 축하해!", wrapper.id)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value(201) }
                    jsonPath("$.data.rollingPaperId") { exists() }
                }

            val rollingPaper = rollingPaperRepository.findAll().single()
            assertEquals("축하요정", rollingPaper.writerNickname)
            assertEquals("생일 축하해!", rollingPaper.content)
            assertTrue(participantRepository.findAll().single().hasWrittenPaper)
        }

        @Test
        fun `인증 회원 롤링페이퍼 작성 성공`() {
            val user = saveUser("member-write")
            val party = saveParty(ownerId = user.id)
            val invite = saveInvite(party, "memberwrite0001")
            val wrapper = saveWrapper()
            val token = tokenProvider.issue(user)

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("회원작성자", "축하해요", wrapper.id)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.data.rollingPaperId") { exists() }
                }

            val participant = assertNotNull(participantRepository.findByPartyAndUser(party, user))
            assertTrue(participant.hasWrittenPaper)
        }

        @Test
        fun `주최자 participant도 롤링페이퍼 작성 성공`() {
            val user = saveUser("host-write")
            val party = saveParty(ownerId = user.id)
            participantRepository.save(Participant(party = party, user = user, isCelebrant = true))
            val invite = saveInvite(party, "hostwrite000001")
            val wrapper = saveWrapper()
            val token = tokenProvider.issue(user)

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("주인공", "나도 축하", wrapper.id)
                }.andExpect {
                    status { isCreated() }
                }
        }

        @Test
        fun `닉네임과 내용은 trim 후 저장`() {
            val party = saveParty()
            val invite = saveInvite(party, "trimwrite000001")
            val wrapper = saveWrapper()

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("  축하요정  ", "  생일 축하해!  ", wrapper.id)
                }.andExpect {
                    status { isCreated() }
                }

            val rollingPaper = rollingPaperRepository.findAll().single()
            assertEquals("축하요정", rollingPaper.writerNickname)
            assertEquals("생일 축하해!", rollingPaper.content)
        }

        @Test
        fun `같은 파티 같은 닉네임은 중복 실패`() {
            val party = saveParty()
            val invite = saveInvite(party, "dupwrite0000001")
            val wrapper = saveWrapper()
            saveRollingPaper(party, wrapper, "축하요정")

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "다른 내용", wrapper.id)
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_NICKNAME_DUPLICATED") }
                }

            assertEquals(1, participantRepository.count())
        }

        @Test
        fun `대소문자만 다른 닉네임도 중복 실패`() {
            val party = saveParty()
            val invite = saveInvite(party, "casewrite000001")
            val wrapper = saveWrapper()
            saveRollingPaper(party, wrapper, "abc")

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("ABC", "다른 내용", wrapper.id)
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_NICKNAME_DUPLICATED") }
                }
        }

        @Test
        fun `대소문자만 다른 닉네임은 DB 제약으로도 중복 실패`() {
            val party = saveParty()
            val wrapper = saveWrapper()
            saveRollingPaper(party, wrapper, "abc")
            val participant = participantRepository.save(Participant(party = party))

            assertFailsWith<DataIntegrityViolationException> {
                rollingPaperRepository.saveAndFlush(
                    RollingPaper(
                        wrapper = wrapper,
                        writer = participant,
                        party = party,
                        writerNickname = "ABC",
                        content = "다른 내용",
                    ),
                )
            }
        }

        @Test
        fun `회원 participant가 이미 작성했으면 실패`() {
            val user = saveUser("already-write")
            val party = saveParty(ownerId = user.id)
            participantRepository.save(Participant(party = party, user = user, hasWrittenPaper = true))
            val invite = saveInvite(party, "alreadywrite001")
            val wrapper = saveWrapper()
            val token = tokenProvider.issue(user)

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("이미작성", "축하해요", wrapper.id)
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_ALREADY_WRITTEN") }
                }
        }

        @Test
        fun `요청 validation 실패`() {
            val party = saveParty()
            val invite = saveInvite(party, "invalidwrite001")

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"writerNickname":"","content":"","wrapperId":null}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("VALIDATION_ERROR") }
                }
        }

        @Test
        fun `없는 wrapperId면 실패`() {
            val party = saveParty()
            val invite = saveInvite(party, "missingwrap0001")

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "생일 축하해!", 9999L)
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("ROLLING_PAPER_WRAPPER_NOT_FOUND") }
                }
        }

        @Test
        fun `만료된 초대 토큰이면 실패`() {
            val party = saveParty()
            val invite = saveInvite(party, "expiredwrite001", LocalDateTime.now().minusMinutes(1))
            val wrapper = saveWrapper()

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "생일 축하해!", wrapper.id)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVITE_LINK_EXPIRED") }
                }
        }

        @Test
        fun `시작 후 7일 지난 파티면 실패`() {
            val party = saveParty(createdAt = LocalDateTime.now().minusDays(8))
            val invite = saveInvite(party, "endedwrite00001")
            val wrapper = saveWrapper()

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "생일 축하해!", wrapper.id)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `잘못된 Bearer 토큰이면 공개 작성 API도 401`() {
            val party = saveParty()
            val invite = saveInvite(party, "invalidjwt00001")
            val wrapper = saveWrapper()

            mockMvc
                .post("/api/v1/party-invites/${invite.token}/rolling-papers") {
                    header("Authorization", "Bearer not-a-jwt")
                    contentType = MediaType.APPLICATION_JSON
                    content = requestBody("축하요정", "생일 축하해!", wrapper.id)
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        private fun saveParty(
            ownerId: Long = 1L,
            createdAt: LocalDateTime = LocalDateTime.now().minusDays(1),
        ): Party {
            val saved =
                partyRepository.saveAndFlush(
                    PaperOnlyParty(
                        ownerId = ownerId,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                )
            saved.createdAt = createdAt
            return partyRepository.saveAndFlush(saved)
        }

        private fun saveInvite(
            party: Party,
            token: String,
            expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        ): PartyInvite =
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    token = token,
                    expiresAt = expiresAt,
                ),
            )

        private fun saveWrapper(): RollingPaperWrapper =
            rollingPaperWrapperRepository.save(RollingPaperWrapper(name = "Topping_Candle"))

        private fun saveRollingPaper(
            party: Party,
            wrapper: RollingPaperWrapper,
            writerNickname: String,
        ): RollingPaper {
            val participant = participantRepository.save(Participant(party = party, hasWrittenPaper = true))
            return rollingPaperRepository.save(
                RollingPaper(
                    wrapper = wrapper,
                    writer = participant,
                    party = party,
                    writerNickname = writerNickname,
                    content = "이미 작성한 내용",
                ),
            )
        }

        private fun saveUser(providerId: String): User =
            userRepository.save(
                User(
                    name = "작성자",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = "$providerId@kakao.local",
                ),
            )

        private fun requestBody(
            writerNickname: String,
            content: String,
            wrapperId: Long,
        ): String =
            """
            {
              "writerNickname": "$writerNickname",
              "content": "$content",
              "wrapperId": $wrapperId
            }
            """.trimIndent()
    }
