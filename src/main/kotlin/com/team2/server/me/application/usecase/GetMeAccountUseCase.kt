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
        // 정상 호출 경로는 JwtAuthenticationFilter 가 userId 존재를 먼저 검증해 401 로 끊는다.
        // 여기서는 UseCase 직접 호출(배치 등) 대비 defence-in-depth 가드
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return MeAccountResponse.from(user, supportProperties.chatUrl)
    }
}
