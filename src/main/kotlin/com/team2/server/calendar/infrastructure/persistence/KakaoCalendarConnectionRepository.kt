package com.team2.server.calendar.infrastructure.persistence

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface KakaoCalendarConnectionRepository : JpaRepository<KakaoCalendarConnection, Long> {
    /**
     * 연동 행을 잠그고 읽는다.
     *
     * 같은 사용자의 요청 둘이 동시에 갱신하면 카카오에 갱신 요청이 두 번 나가고, 카카오가 새 리프레시
     * 토큰을 발급하며 기존 것을 폐기하면 한쪽이 무효한 토큰을 저장해 연동이 깨진다. 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByUserId(userId: Long): KakaoCalendarConnection?
}
