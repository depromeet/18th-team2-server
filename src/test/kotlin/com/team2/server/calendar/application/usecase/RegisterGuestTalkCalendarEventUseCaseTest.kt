package com.team2.server.calendar.application.usecase

import com.team2.server.calendar.application.dto.GuestCalendarRegistrationResult
import com.team2.server.calendar.application.dto.KakaoConsentCredentials
import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        private val transactionManager: PlatformTransactionManager,
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

        /**
         * 선검사를 통과한 뒤 UNIQUE 제약에 걸린 요청이 예외를 전파하는지 본다. 결과값으로 흡수하면 안 된다.
         *
         * 예약을 시도한 트랜잭션 안에서 제약 위반을 잡고 정상 반환하면, Hibernate 가 이미 세션을
         * rollback-only 로 표시했으므로 커밋이 `UnexpectedRollbackException` 으로 깨진다. 경쟁에서 진
         * 요청은 반드시 예외로 끝나야 하고 결과 매핑은 트랜잭션 밖(컨트롤러)에서 한다.
         *
         * 스레드 없이 경쟁 상태를 만든다. MySQL REPEATABLE READ 는 트랜잭션의 첫 읽기에서 스냅샷을
         * 고정하므로, 스냅샷을 잡은 뒤 별도 트랜잭션으로 같은 키의 행을 커밋하면 선검사(`existsForGuest`)는
         * 그 행을 보지 못하고 INSERT 만 UNIQUE 인덱스에 걸린다 — 동시 요청 둘이 겹친 상황과 같은 상태다.
         */
        @Test
        fun `선검사를 통과한 뒤 유니크 충돌이 나면 예외를 전파한다`() {
            val fixture = saveHostPartyAndInvite(LocalDateTime.now().plusDays(2))

            val thrown =
                assertFailsWith<BusinessException> {
                    inTransaction {
                        calendarRegistrationRepository.count()
                        commitGuestRegistrationInNewTransaction(fixture.partyId)
                        registerGuestTalkCalendarEventUseCase(fixture.inviteToken, credentials)
                    }
                }

            assertEquals(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS, thrown.errorCode)
            assertEquals(1, calendarRegistrationRepository.findAll().size)
            assertNull(calendarRegistrationRepository.findAll().single().eventId)
            assertNull(KakaoStubServers.requests[KakaoStubServers.CREATE_EVENT_PATH])
        }

        private fun inTransaction(block: () -> Unit) =
            TransactionTemplate(transactionManager).executeWithoutResult { block() }

        /** 선검사가 끝난 뒤 다른 요청이 같은 자리를 차지한 상태를 만든다. */
        private fun commitGuestRegistrationInNewTransaction(partyId: Long) {
            TransactionTemplate(transactionManager)
                .apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
                .executeWithoutResult {
                    calendarRegistrationRepository.saveAndFlush(
                        CalendarRegistration(
                            partyId = partyId,
                            provider = CalendarProvider.KAKAO_TALK,
                            kakaoProviderId = credentials.kakaoProviderId,
                        ),
                    )
                }
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
