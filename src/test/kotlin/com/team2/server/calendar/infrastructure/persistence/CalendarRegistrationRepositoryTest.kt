package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.support.JpaSliceTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CalendarRegistrationRepositoryTest
    @Autowired
    constructor(
        private val calendarRegistrationRepository: CalendarRegistrationRepository,
    ) : JpaSliceTestSupport() {
        @Test
        fun `userId partyId provider 로 등록 이력을 조회한다`() {
            calendarRegistrationRepository.save(
                CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
            )

            val found =
                calendarRegistrationRepository.findByUserIdAndPartyIdAndProvider(
                    userId = 1L,
                    partyId = 2L,
                    provider = CalendarProvider.KAKAO_TALK,
                )

            assertEquals(1L, found?.userId)
            assertEquals(2L, found?.partyId)
            assertNull(found?.eventId)
        }

        @Test
        fun `같은 사용자 파티 provider 조합은 두 번 저장할 수 없다`() {
            calendarRegistrationRepository.saveAndFlush(
                CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
            )

            assertFailsWith<DataIntegrityViolationException> {
                calendarRegistrationRepository.saveAndFlush(
                    CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
                )
            }
        }

        @Test
        fun `linkEvent 로 카카오 일정 ID를 채운다`() {
            val saved =
                calendarRegistrationRepository.saveAndFlush(
                    CalendarRegistration(userId = 1L, partyId = 2L, provider = CalendarProvider.KAKAO_TALK),
                )

            saved.linkEvent("event-1")
            calendarRegistrationRepository.flush()

            assertEquals("event-1", calendarRegistrationRepository.findById(saved.id).get().eventId)
        }
    }
