package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.CalendarRegistration
import com.team2.server.calendar.domain.vo.CalendarProvider
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface CalendarRegistrationRepository : JpaRepository<CalendarRegistration, Long> {
    /**
     * 등록 이력을 잠그고 읽는다.
     *
     * 이미 이력이 있는데 사용자가 카카오 앱에서 일정을 지운 경우, 동시 요청 둘이 모두 갱신 실패(404)를 받고
     * 각자 일정을 새로 만들어 캘린더에 중복이 남는다. 신규 등록 경로는 UNIQUE 제약이 막아주지만
     * 이 경로에는 INSERT 가 없어 제약이 개입하지 않으므로 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserIdAndPartyIdAndProvider(
        userId: Long,
        partyId: Long,
        provider: CalendarProvider,
    ): CalendarRegistration?
}
