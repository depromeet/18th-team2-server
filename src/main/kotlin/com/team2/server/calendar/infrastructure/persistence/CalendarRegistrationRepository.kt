package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarRegistrationRepository : JpaRepository<CalendarRegistration, Long> {
    fun findByUserIdAndPartyIdAndProvider(
        userId: Long,
        partyId: Long,
        provider: CalendarProvider,
    ): CalendarRegistration?
}
