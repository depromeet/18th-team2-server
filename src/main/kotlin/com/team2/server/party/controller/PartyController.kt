package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.JoinPartyRequest
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.service.PartyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val partyService: PartyService,
) {
    @Operation(
        summary = "파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping()
    fun createParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> = ApiResponse.success(partyService.createParty(principal.userId, request))

    @Operation(summary = "파티 정보 조회", description = "초대 토큰으로 파티 정보를 조회한다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "파티 정보 조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = PartyInfoResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "만료된 초대 토큰 (INVITE_LINK_EXPIRED)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "존재하지 않는 파티 또는 초대 토큰 (PARTY_NOT_FOUND)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/{inviteToken}")
    fun getPartyInfo(
        @PathVariable inviteToken: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<PartyInfoResponse> = ApiResponse.success(partyService.getPartyInfo(inviteToken, principal?.userId))

    @Operation(summary = "파티 참여", description = "초대 토큰으로 파티에 참여한다. 회원/비회원 모두 가능.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "파티 참여 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ParticipantResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 요청, 만료된 초대 토큰, 종료된 파티, 캐릭터 선택 규칙 위반",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "존재하지 않는 파티 또는 캐릭터 (PARTY_NOT_FOUND, CHARACTER_NOT_FOUND)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "409",
                description = "이미 참여한 파티 (ALREADY_JOINED)",
                content = [
                    Content(
                        schema = Schema(implementation = ErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/{inviteToken}/participants")
    fun joinParty(
        @PathVariable inviteToken: String,
        @Valid @RequestBody request: JoinPartyRequest,
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ApiResponse<ParticipantResponse> =
        ApiResponse.success(
            partyService.joinParty(
                inviteToken,
                principal?.userId,
                request.nickname,
                request.characterId,
            ),
        )
}
