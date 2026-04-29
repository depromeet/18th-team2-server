package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.JoinPartyRequest
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.service.PartyParticipationService
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
    private val partyParticipationService: PartyParticipationService,
) {
    @Operation(
        summary = "실시간 파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/realtime")
    fun createRealtimeParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(partyService.createParty(principal.userId, request, PartyOption.REALTIME))

    @Operation(
        summary = "롤링페이퍼 파티 생성",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/paper")
    fun createPaperOnlyParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse> =
        ApiResponse.success(partyService.createParty(principal.userId, request, PartyOption.PAPER_ONLY))

    @Operation(summary = "파티 정보 조회", description = "초대 토큰으로 파티 정보를 조회한다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "파티 정보 조회 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ApiResponse::class),
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

    @Operation(
        summary = "파티 참여",
        description =
            "초대 토큰으로 파티에 참여한다. 회원/비회원 모두 가능. " +
                "채팅 허용 파티는 characterId가 필수이며 누락 시 CHARACTER_REQUIRED를 반환한다. " +
                "채팅 비허용 파티는 characterId를 보낼 수 없으며 전달 시 CHARACTER_NOT_ALLOWED를 반환한다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "파티 참여 성공",
                content = [
                    Content(
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description =
                    "잘못된 요청, 만료된 초대 토큰, 종료된 파티, 캐릭터 선택 규칙 위반 " +
                        "(INVITE_LINK_EXPIRED, PARTY_ENDED, CHARACTER_REQUIRED, CHARACTER_NOT_ALLOWED)",
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
            partyParticipationService.joinParty(
                inviteToken,
                principal?.userId,
                request.nickname,
                request.characterId,
            ),
        )
}
