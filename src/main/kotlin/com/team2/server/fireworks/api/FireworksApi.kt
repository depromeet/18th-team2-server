package com.team2.server.fireworks.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Fireworks", description = "폭죽 애니메이션 API")
interface FireworksApi {
    @Operation(
        summary = "폭죽 트리거",
        description = """
파티 참가자가 폭죽 아이콘을 클릭하면 해당 파티의 모든 SSE 구독자에게 `fireworks` 이벤트를 broadcast합니다.

**인증:** 로그인 사용자는 `Authorization: Bearer {token}`, 비로그인 참여자는 `X-Participant-Token: {token}` 헤더 사용.

**broadcast되는 SSE 이벤트**
```
event: fireworks
data: {
  "partyId": 1,
  "participantId": 5,
  "nickname": "토끼왕"
}
```
""",
    )
    @SwaggerApiResponse(responseCode = "204", description = "폭죽 트리거 성공")
    @SwaggerApiResponse(
        responseCode = "400",
        description = "파티가 LIVE_OPEN 상태가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_ACTIVE","message":"현재 채팅이 활성화된 시간이 아닙니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "401",
        description = "인증 실패",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """{"status":401,"error":{"code":"UNAUTHORIZED","message":"로그인이 필요합니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "403",
        description = "파티 참가자가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """{"status":403,"error":{"code":"PARTY_FORBIDDEN","message":"파티에 대한 권한이 없습니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "파티 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value = """{"status":404,"error":{"code":"PARTY_NOT_FOUND","message":"파티를 찾을 수 없습니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun triggerFireworks(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    )
}
