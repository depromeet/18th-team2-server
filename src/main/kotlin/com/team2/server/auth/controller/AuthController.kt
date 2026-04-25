package com.team2.server.auth.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ApiResponse
import com.team2.server.user.repository.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userRepository: UserRepository,
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ApiResponse<UserResponse> {
        val user = userRepository.findById(principal.userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
        return ApiResponse.success(UserResponse.from(user))
    }
}
