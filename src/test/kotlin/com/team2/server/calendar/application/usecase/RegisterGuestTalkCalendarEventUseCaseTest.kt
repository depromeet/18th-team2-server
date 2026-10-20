package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.support.IntegrationTestSupport
import com.team2.server.support.KakaoStubServers
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegisterGuestTalkCalendarEventUseCaseTest
    @Autowired
    constructor(
        private val registerGuestTalkCalendarEventUseCase: RegisterGuestTalkCalendarEventUseCase,
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
        private val userRepository: UserRepository,
        private val partyRepository: PartyRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val databaseCleanup: DatabaseCleanup,
    ) : IntegrationTestSupport() {
        private val credentials =
            KakaoConsentCredentials(accessToken = "guest-access", kakaoProviderId = "4321")

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
            KakaoStubServers.reset()
        }

        @Test
        fun `등록에 성공하면 REGISTERED 이고 비회원 행이 남는다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.REGISTERED, result)
            val saved = calendarRegistrationRepository.findAll().single()
            assertEquals(fixture.partyId, saved.partyId)
            assertEquals("4321", saved.kakaoProviderId)
            assertNull(saved.userId)
            assertNotNull(saved.eventId)
            assertNotNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `이미 등록했으면 카카오를 부르지 않고 ALREADY_REGISTERED 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)
            KakaoStubServers.reset()

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.ALREADY_REGISTERED, result)
            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `다른 회원번호면 같은 파티에 각각 등록된다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))
            registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            val result =
                registerGuestTalkCalendarEventUseCase(
                    fixture.inviteToken,
                    credentials.copy(kakaoProviderId = "9999"),
                )

            assertEquals(GuestCalendarRegistrationResult.REGISTERED, result)
            assertEquals(2, calendarRegistrationRepository.findAll().size)
        }

        @Test
        fun `파티가 이미 시작됐으면 PARTY_ALREADY_STARTED 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().minusMinutes(1))

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.PARTY_ALREADY_STARTED, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        @Test
        fun `없는 초대 토큰이면 INVITE_INVALID 다`() {
            val result = registerGuestTalkCalendarEventUseCase("nosuchtk", credentials)

            assertEquals(GuestCalendarRegistrationResult.INVITE_INVALID, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        @Test
        fun `만료된 초대 토큰이면 INVITE_INVALID 다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2), inviteExpired = true)

            val result = registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)

            assertEquals(GuestCalendarRegistrationResult.INVITE_INVALID, result)
            assertTrue(calendarRegistrationRepository.findAll().isEmpty())
        }

        private data class GuestFixture(
            val partyId: Long,
            val inviteToken: String,
        )

        private var inviteSequence = 0

        private fun saveHostPartyAndInvite(
            startedAt: LocalDateTime,
            inviteExpired: Boolean = false,
        ): GuestFixture {
            val host =
                userRepository.save(
                    User(
                        name = "호스트",
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = "calendar-host",
                        email = "calendar-host@test.local",
                    ),
                )
            val party =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = host.id,
                        startedAt = startedAt,
                        celebrantNickname = "지민",
                    ),
                )
            val token = "guest%09d".format(inviteSequence++)
            val expiresAt = if (inviteExpired) LocalDateTime.now().minusDays(1) else LocalDateTime.now().plusDays(7)
            val invite =
                partyInviteRepository.save(
                    PartyInvite(party = party, token = token, expiresAt = expiresAt),
                )
            return GuestFixture(partyId = party.id, inviteToken = invite.token)
        }

        companion object {
            @JvmStatic
            @BeforeAll
            fun startStub() = KakaoStubServers.start()

            @JvmStatic
            @AfterAll
            fun stopStub() = KakaoStubServers.stop()
        }
    }
