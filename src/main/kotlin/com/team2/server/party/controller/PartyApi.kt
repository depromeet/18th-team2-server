package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.common.swagger.ValidationErrorResponse
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.dto.JoinPartyRequest
import com.team2.server.party.dto.ParticipantResponse
import com.team2.server.party.dto.PartyInfoResponse
import com.team2.server.party.entity.PartyOption
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface PartyApi {
    @Operation(
        summary = "파티 생성 (REALTIME | PAPER_ONLY)",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(
        responseCode = "201",
        description = "파티 생성 성공",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 200,
                              "data": {
                                "partyId": 1
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun createParty(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 옵션", example = "REALTIME") partyOption: PartyOption,
        request: CreatePartyRequest,
    ): ApiResponse<CreatePartyResponse>

    @Operation(
        summary = "파티 정보 조회",
        description = "초대 토큰으로 파티 정보를 조회한다. 인증은 선택사항이며, 토큰이 있으면 회원 정보를 활용한다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
            SecurityRequirement(name = ""),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "파티 정보 조회 성공",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 200,
                              "data": {
                                "name": "생일파티",
                                "celebrantNickname": "홍길동",
                                "purpose": "BIRTHDAY",
                                "option": "CHAT_ALLOWED",
                                "startedAt": "2024-11-26T14:30:00",
                                "endedAt": null,
                                "ended": false,
                                "myParticipant": null
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "만료된 초대 토큰",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "INVITE_LINK_EXPIRED",
                                "message": "만료된 초대링크입니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getPartyInfo(
        @Parameter(description = "초대 토큰", example = "a1b2c3d4e5f67890") inviteToken: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
    ): ApiResponse<PartyInfoResponse>

    @Operation(
        summary = "파티 참여",
        description =
            "초대 토큰으로 파티에 참여한다. 회원/비회원 모두 가능. " +
                "채팅 허용 파티는 characterId가 필수이며 누락 시 CHARACTER_REQUIRED를 반환한다. " +
                "채팅 비허용 파티는 characterId를 보낼 수 없으며 전달 시 CHARACTER_NOT_ALLOWED를 반환한다.",
        security = [
            SecurityRequirement(name = "Bearer Authentication"),
            SecurityRequirement(name = ""),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "파티 참여 성공",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ApiResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 200,
                              "data": {
                                "participantId": 10,
                                "nickname": "홍길동",
                                "characterImageUrl": "/images/characters/character1.jpg"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "잘못된 요청 (만료된 초대 토큰, 종료된 파티, 캐릭터 선택 규칙 위반)",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "만료된 초대링크",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "INVITE_LINK_EXPIRED",
                                "message": "만료된 초대링크입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "종료된 파티",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_ENDED",
                                "message": "이미 종료된 파티입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "캐릭터 선택 필수",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "CHARACTER_REQUIRED",
                                "message": "채팅 허용 파티는 캐릭터 선택이 필수입니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "캐릭터 선택 불가",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "CHARACTER_NOT_ALLOWED",
                                "message": "채팅 비허용 파티는 캐릭터를 선택할 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 파티 또는 캐릭터",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "PARTY_NOT_FOUND",
                                "message": "파티를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                    ExampleObject(
                        name = "캐릭터 없음",
                        value = """
                            {
                              "status": 404,
                              "error": {
                                "code": "CHARACTER_NOT_FOUND",
                                "message": "캐릭터를 찾을 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "이미 참여한 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """
                            {
                              "status": 409,
                              "error": {
                                "code": "ALREADY_JOINED",
                                "message": "이미 참여한 파티입니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @ValidationErrorResponse
    @InternalServerErrorResponse
    fun joinParty(
        @Parameter(description = "초대 토큰", example = "a1b2c3d4e5f67890") inviteToken: String,
        request: JoinPartyRequest,
        @Parameter(hidden = true) principal: UserPrincipal?,
    ): ApiResponse<ParticipantResponse>
}
