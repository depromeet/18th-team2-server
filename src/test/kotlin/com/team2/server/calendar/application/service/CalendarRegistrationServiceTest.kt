package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.support.JpaSliceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 실제 MySQL 을 거쳐야 UNIQUE 제약 위반 → CALENDAR_REGISTRATION_IN_PROGRESS 변환이 검증된다.
 * 제약 이름 문자열이 마이그레이션과 어긋나면 이 테스트가 잡는다.
 */
class CalendarRegistrationServiceTest
    @Autowired
    constructor(
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
    ) : JpaSliceTestSupport() {
        private val service = CalendarRegistrationService(calendarRegistrationRepository)

        @Test
        fun `이력이 없으면 null 을 반환한다`() {
            assertNull(service.find(userId = 1L, partyId = 2L))
        }

        @Test
        fun `reserve 는 event_id 가 비어 있는 KAKAO_TALK 이력을 만든다`() {
            val reserved = service.reserve(userId = 1L, partyId = 2L)

            assertEquals(1L, reserved.userId)
            assertEquals(2L, reserved.partyId)
            assertEquals(CalendarProvider.KAKAO_TALK, reserved.provider)
            assertNull(reserved.eventId)
        }

        @Test
        fun `reserve 직후 find 로 같은 이력을 읽는다`() {
            service.reserve(userId = 1L, partyId = 2L)

            val found = service.find(userId = 1L, partyId = 2L)

            assertEquals(1L, found?.userId)
            assertEquals(2L, found?.partyId)
        }

        @Test
        fun `같은 사용자 파티로 reserve 를 두 번 하면 CALENDAR_REGISTRATION_IN_PROGRESS`() {
            service.reserve(userId = 1L, partyId = 2L)

            val exception =
                assertFailsWith<BusinessException> {
                    service.reserve(userId = 1L, partyId = 2L)
                }

            assertEquals(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS, exception.errorCode)
        }

        @Test
        fun `사용자나 파티가 다르면 각각 reserve 할 수 있다`() {
            service.reserve(userId = 1L, partyId = 2L)
            service.reserve(userId = 1L, partyId = 3L)
            service.reserve(userId = 9L, partyId = 2L)

            assertEquals(3, calendarRegistrationRepository.findAll().size)
        }

        @Test
        fun `linkEvent 는 카카오 일정 ID 를 이력에 채운다`() {
            val reserved = service.reserve(userId = 1L, partyId = 2L)

            service.linkEvent(reserved, "event-1")
            calendarRegistrationRepository.flush()

            assertEquals("event-1", service.find(userId = 1L, partyId = 2L)?.eventId)
        }

        @Test
        fun `linkEvent 는 기존 일정 ID 를 덮어쓴다`() {
            val registration =
                calendarRegistrationRepository.saveAndFlush(
                    CalendarRegistration(
                        userId = 1L,
                        partyId = 2L,
                        provider = CalendarProvider.KAKAO_TALK,
                        eventId = "event-1",
                    ),
                )

            service.linkEvent(registration, "event-2")
            calendarRegistrationRepository.flush()

            assertEquals("event-2", service.find(userId = 1L, partyId = 2L)?.eventId)
        }
    }
