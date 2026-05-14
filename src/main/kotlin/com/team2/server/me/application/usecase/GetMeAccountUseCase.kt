package com.team2.server.me.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.me.api.dto.MeAccountResponse
import com.team2.server.me.config.SupportProperties
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMeAccountUseCase(
    private val userRepository: UserRepository,
    private val supportProperties: SupportProperties,
) {
    @Transactional(readOnly = true)
    fun invoke(userId: Long): MeAccountResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return MeAccountResponse.from(user, supportProperties.chatUrl)
    }
}
