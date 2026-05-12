package com.team2.server.auth.controller

import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Dev", description = "개발용 API (prod 환경에서 비활성화)")
@Profile("!prod")
@RestController
@RequestMapping("/api/dev")
class DevTokenController(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    @Operation(summary = "개발용 JWT 토큰 발급 (존재하지 않으면 유저 자동 생성)")
    @PostMapping("/token")
    fun issueToken(
        @RequestParam email: String,
    ): ApiResponse<DevTokenResponse> {
        val user =
            userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, email)
                ?: userRepository.save(
                    User(
                        name = email,
                        birthDay = "01-01",
                        provider = AuthProvider.KAKAO,
                        providerId = email,
                        email = email,
                    ),
                )
        val token = jwtTokenProvider.issue(user)
        return ApiResponse.success(DevTokenResponse(token = token, userId = user.id))
    }

    @Operation(summary = "개발용 JWT 토큰 발급 (userId로)")
    @PostMapping("/token/by-id")
    fun issueTokenById(
        @RequestParam userId: Long,
    ): ApiResponse<DevTokenResponse> {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        val token = jwtTokenProvider.issue(user)
        return ApiResponse.success(DevTokenResponse(token = token, userId = user.id))
    }
}

data class DevTokenResponse(
    val token: String,
    val userId: Long,
)
