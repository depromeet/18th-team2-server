package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.api.dto.CreatePartyResponse
import com.team2.server.party.api.dto.CreateRealtimePartyRequest
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.usecase.CreatePaperOnlyPartyUseCase
import com.team2.server.party.application.usecase.CreateRealtimePartyUseCase
import com.team2.server.party.application.usecase.DeletePartyUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val createRealtimePartyUseCase: CreateRealtimePartyUseCase,
    private val createPaperOnlyPartyUseCase: CreatePaperOnlyPartyUseCase,
    private val deletePartyUseCase: DeletePartyUseCase,
) : PartyApi {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/realtime")
    override fun createRealtimeParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateRealtimePartyRequest,
    ): ApiResponse<CreatePartyResponse> {
        val partyId =
            createRealtimePartyUseCase.invoke(
                userId = principal.userId,
                command =
                    CreateRealtimePartyCommand(
                        celebrantNickname = request.celebrantNickname,
                        startedDate = request.startedDate,
                        startTime = request.startTime,
                        characterId = request.characterId,
                    ),
            )
        return ApiResponse.success(HttpStatus.CREATED, CreatePartyResponse(partyId = partyId))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/paper-only")
    override fun createPaperOnlyParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePaperOnlyPartyRequest,
    ): ApiResponse<CreatePartyResponse> {
        val partyId =
            createPaperOnlyPartyUseCase.invoke(
                userId = principal.userId,
                command =
                    CreatePaperOnlyPartyCommand(
                        celebrantNickname = request.celebrantNickname,
                        startedDate = request.startedDate,
                    ),
            )
        return ApiResponse.success(HttpStatus.CREATED, CreatePartyResponse(partyId = partyId))
    }

    @PostMapping("/{partyType}")
    fun createPartyUnknownType(): Nothing = throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

    @DeleteMapping("/{partyId}")
    override fun deleteParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<Unit> {
        deletePartyUseCase.delete(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(HttpStatus.OK, Unit)
    }
}
