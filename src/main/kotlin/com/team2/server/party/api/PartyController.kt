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
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.dto.RealtimePartyEndStatusResult
import com.team2.server.party.application.dto.RealtimePartyNextActionResult
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.application.usecase.CreatePaperOnlyPartyUseCase
import com.team2.server.party.application.usecase.CreateRealtimePartyUseCase
import com.team2.server.party.application.usecase.DeletePartyUseCase
import com.team2.server.party.application.usecase.GetRealtimePartyEndStatusUseCase
import com.team2.server.party.application.usecase.GetRealtimePartyNextActionUseCase
import com.team2.server.party.application.usecase.GetRealtimePartyStateUseCase
import com.team2.server.party.application.usecase.StartRealtimePartyEndUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val createRealtimePartyUseCase: CreateRealtimePartyUseCase,
    private val createPaperOnlyPartyUseCase: CreatePaperOnlyPartyUseCase,
    private val deletePartyUseCase: DeletePartyUseCase,
    private val getRealtimePartyEndStatusUseCase: GetRealtimePartyEndStatusUseCase,
    private val startRealtimePartyEndUseCase: StartRealtimePartyEndUseCase,
    private val getRealtimePartyStateUseCase: GetRealtimePartyStateUseCase,
    private val getRealtimePartyNextActionUseCase: GetRealtimePartyNextActionUseCase,
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

    @GetMapping("/{partyId}/realtime-end")
    override fun getRealtimeEndStatus(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<RealtimePartyEndStatusResult> =
        ApiResponse.success(HttpStatus.OK, getRealtimePartyEndStatusUseCase(partyId, principal.userId))

    @PostMapping("/{partyId}/realtime-end")
    override fun startRealtimeEnd(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<RealtimePartyEndResult> =
        ApiResponse.success(HttpStatus.OK, startRealtimePartyEndUseCase(partyId, principal.userId))

    @GetMapping("/{partyId}/realtime-state")
    override fun getRealtimeState(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
    ): ApiResponse<RealtimePartyStateResult> =
        ApiResponse.success(
            HttpStatus.OK,
            getRealtimePartyStateUseCase(partyId, principal?.userId, participantToken),
        )

    @GetMapping("/{partyId}/realtime-next-action")
    override fun getRealtimeNextAction(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @PathVariable partyId: Long,
    ): ApiResponse<RealtimePartyNextActionResult> =
        ApiResponse.success(
            HttpStatus.OK,
            getRealtimePartyNextActionUseCase(partyId, principal?.userId, participantToken),
        )
}
