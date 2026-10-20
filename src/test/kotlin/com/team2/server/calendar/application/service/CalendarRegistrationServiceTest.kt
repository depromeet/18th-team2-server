package com.team2.server.calendar.application.service

import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.support.JpaSliceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalendarRegistrationServiceTest
    @Autowired
    constructor(
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
    ) : JpaSliceTestSupport() {
        private val service = CalendarRegistrationService(calendarRegistrationRepository)

        @Test
        fun `비회원 등록을 예약하면 회원번호로 조회된다`() {
            val registration = service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            assertNotNull(registration.id)
            assertEquals("4321", registration.kakaoProviderId)
            assertTrue(service.existsForGuest(kakaoProviderId = "4321", partyId = 170L))
        }

        @Test
        fun `같은 회원번호와 파티로 두 번 예약하면 진행 중 예외가 난다`() {
            service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            val exception =
                runCatching { service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L) }
                    .exceptionOrNull()

            assertEquals(
                ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS,
                (exception as BusinessException).errorCode,
            )
        }

        @Test
        fun `다른 파티면 같은 회원번호로 예약할 수 있다`() {
            service.reserveForGuest(kakaoProviderId = "4321", partyId = 170L)

            val other = service.reserveForGuest(kakaoProviderId = "4321", partyId = 171L)

            assertNotNull(other.id)
        }

        @Test
        fun `등록이 없으면 존재하지 않는다고 답한다`() {
            assertFalse(service.existsForGuest(kakaoProviderId = "9999", partyId = 170L))
        }
    }
