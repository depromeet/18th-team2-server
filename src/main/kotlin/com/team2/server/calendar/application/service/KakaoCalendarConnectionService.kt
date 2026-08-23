package com.team2.server.calendar.application.service

import com.team2.server.calendar.domain.entity.KakaoCalendarConnection
import com.team2.server.calendar.infrastructure.persistence.KakaoCalendarConnectionRepository
import org.springframework.stereotype.Service

@Service
class KakaoCalendarConnectionService(
    private val kakaoCalendarConnectionRepository: KakaoCalendarConnectionRepository,
) {
    fun find(userId: Long): KakaoCalendarConnection? = kakaoCalendarConnectionRepository.findByUserId(userId)

    fun save(connection: KakaoCalendarConnection): KakaoCalendarConnection =
        kakaoCalendarConnectionRepository.save(connection)

    fun delete(connection: KakaoCalendarConnection) {
        kakaoCalendarConnectionRepository.delete(connection)
        kakaoCalendarConnectionRepository.flush()
    }
}
