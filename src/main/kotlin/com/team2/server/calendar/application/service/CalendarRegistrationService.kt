package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import com.team2.server.calendar.infrastructure.persistence.CalendarRegistrationRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class CalendarRegistrationService(
    private val calendarRegistrationRepository: CalendarRegistrationRepository,
) {
    fun find(
        userId: Long,
        partyId: Long,
    ): CalendarRegistration? =
        calendarRegistrationRepository.findByUserIdAndPartyIdAndProvider(
            userId = userId,
            partyId = partyId,
            provider = CalendarProvider.KAKAO_TALK,
        )

    /**
     * 카카오 호출 전에 등록 자리를 먼저 잡는다.
     * 동시에 들어온 두 번째 요청은 UNIQUE 제약에 걸려 여기서 막힌다.
     */
    fun reserve(
        userId: Long,
        partyId: Long,
    ): CalendarRegistration =
        try {
            calendarRegistrationRepository.saveAndFlush(
                CalendarRegistration(
                    userId = userId,
                    partyId = partyId,
                    provider = CalendarProvider.KAKAO_TALK,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (e.isConstraintViolation(CalendarRegistration.UK_USER_PARTY_PROVIDER)) {
                throw BusinessException(ErrorCode.CALENDAR_REGISTRATION_IN_PROGRESS)
            }
            throw e
        }

    fun linkEvent(
        registration: CalendarRegistration,
        eventId: String,
    ) {
        registration.linkEvent(eventId)
        calendarRegistrationRepository.save(registration)
    }
}
