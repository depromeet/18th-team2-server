package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import com.team2.server.party.application.usecase.GetMyArchiveUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(
    private val getMyArchiveUseCase: GetMyArchiveUseCase,
) : ArchiveApi {
    @GetMapping
    override fun getArchive(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
    ): ApiResponse<ArchiveListResponse> {
        validateCursor(cursor)
        validateSize(size)
        return ApiResponse.success(getMyArchiveUseCase.invoke(principal?.userId, cursor, size))
    }

    private fun validateCursor(cursor: Long?) {
        if (cursor != null && cursor < 1) throw BusinessException(ErrorCode.INVALID_INPUT)
    }

    private fun validateSize(size: Int) {
        if (size < MIN_SIZE || size > MAX_SIZE) throw BusinessException(ErrorCode.INVALID_INPUT)
    }

    companion object {
        private const val DEFAULT_SIZE = 20
        private const val MIN_SIZE = 1
        private const val MAX_SIZE = 50
    }
}
