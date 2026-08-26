package com.team2.server.calendar.infrastructure.user

import com.team2.server.calendar.application.port.CalendarUserPort
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class CalendarUserAdapter(
    private val userRepository: UserRepository,
) : CalendarUserPort {
    override fun findUserIdByKakaoProviderId(providerId: String): Long? =
        userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)?.id
}
