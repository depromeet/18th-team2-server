@file:Suppress("MaxLineLength")

package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.common.web.ApiResponse
import com.team2.server.common.web.ErrorResponse
import com.team2.server.common.web.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Chat", description = "실시간 채팅 API")
interface ChatApi {
    @Operation(
        summary = "실시간 파티 입장 및 채팅 구독",
        description = """
초대 토큰으로 실시간 파티에 입장하고, 즉시 채팅 스트림을 구독합니다.

**⚠️ Swagger UI에서는 직접 실행이 불가합니다.** 아래 curl 명령어를 사용하세요.

**입장 가능 조건**
- 초대 링크가 만료되지 않아야 합니다
- 신규 입장은 실시간 파티가 LIVE_OPEN 상태일 때만 가능합니다

**재입장**
기존 participantToken으로 다시 호출하면 LIVE_ENDING 상태에서도 SSE 스트림을 복구할 수 있습니다.

---

**연결 직후 수신되는 이벤트**

| 이벤트 이름 | 설명 |
|---|---|
| `party-state` | 현재 실시간 파티 상태 |
| `entered` | 입장 완료 정보 (participantToken + 기존 채팅 내역) |

**이후 실시간으로 수신되는 이벤트**

| 이벤트 이름 | 설명 |
|---|---|
| `message` | 새로 전송된 메시지 1건 |
| `user-entered` | 참여자 입장 알림 |
| `user-left` | 참여자 퇴장 알림 |
| `host-end-available` | 주최자 수동 종료 가능 알림 |
| `party-ending` | 60초 종료 카운트다운 시작 알림 |
| `party-ended` | 실시간 파티 종료 알림 |

---

**`entered` 이벤트 데이터 구조**
```json
{
  "participantToken": "aB3xZ9qR",
  "messages": [
    {
      "messageId": 1,
      "content": "안녕하세요!",
      "senderNickname": "토끼왕",
      "senderCharacterId": 2,
      "senderCharacterImageUrl": "https://example.com/rabbit.png",
      "senderRole": "PARTICIPANT",
      "sentAt": "2026-05-07T10:00:00"
    }
  ]
}
```

**`message` 이벤트 데이터 구조**
```json
{
  "messageId": 2,
  "content": "반갑습니다!",
  "senderNickname": "곰돌이",
  "senderCharacterId": null,
  "senderCharacterImageUrl": null,
  "senderRole": "CELEBRANT",
  "sentAt": "2026-05-07T10:01:00"
}
```

> `participantToken`: 이후 메시지 전송(`X-Participant-Token` 헤더)과 SSE 재연결에 사용합니다.
> `senderCharacterId`는 캐릭터를 선택하지 않은 경우 `null`입니다.

---

**curl 예시 (비로그인)**
```
curl -N -X POST http://localhost:8080/api/v1/party-invites/{inviteToken}/realtime-participants/stream \
  -H "Content-Type: application/json" \
  -d '{"nickname":"토끼왕","characterId":1}'
```

**curl 예시 (로그인)**
```
curl -N -X POST http://localhost:8080/api/v1/party-invites/{inviteToken}/realtime-participants/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"nickname":"토끼왕","characterId":1}'
```

연결은 **15분** 후 자동 종료됩니다.
""",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "SSE 스트림 연결 성공",
        content = [Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = Schema(type = "string"))],
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "입장 불가",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "초대 링크 만료",
                        value = """{"status":400,"error":{"code":"INVITE_LINK_EXPIRED","message":"만료된 초대링크입니다"}}""",
                    ),
                    ExampleObject(
                        name = "입장 불가 상태 (파티 미시작·종료)",
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_ACTIVE","message":"현재 채팅이 활성화된 시간이 아닙니다"}}""",
                    ),
                    ExampleObject(
                        name = "실시간 파티가 아님",
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_SUPPORTED","message":"채팅을 지원하지 않는 파티입니다"}}""",
                    ),
                    ExampleObject(
                        name = "주최자 닉네임 변경 불가 (재입장 시)",
                        value =
                            """{"status":400,"error":{"code":"PARTY_HOST_NICKNAME_NOT_EDITABLE","message":"주최자 닉네임은 변경할 수 없습니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "리소스 없음",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "존재하지 않는 초대 토큰",
                        value = """{"status":404,"error":{"code":"PARTY_NOT_FOUND","message":"파티를 찾을 수 없습니다"}}""",
                    ),
                    ExampleObject(
                        name = "존재하지 않는 캐릭터",
                        value = """{"status":404,"error":{"code":"CHARACTER_NOT_FOUND","message":"캐릭터를 찾을 수 없습니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "409",
        description = "닉네임 중복",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value =
                            """{"status":409,"error":{"code":"PARTY_NICKNAME_DUPLICATED","message":"이미 사용 중인 닉네임입니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @InternalServerErrorResponse
    fun enterAndSubscribe(
        @Parameter(description = "초대 토큰") inviteToken: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
        request: EnterRealtimePartyRequest,
    ): SseEmitter

    @Operation(
        summary = "채팅 메시지 전송",
        description = """
실시간 파티 채팅에 메시지를 전송합니다.

**전송 가능 조건**
파티가 시작된 상태(`LIVE_OPEN`)에서만 전송할 수 있습니다. 파티 시작 전이나 종료 후에는 전송이 거부됩니다.

**인증**
로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참여자는 `X-Participant-Token: {participantToken}` 헤더를 사용합니다. 둘 중 하나는 반드시 포함해야 합니다.

**전송 후 처리**
메시지가 저장되면 해당 파티의 SSE 구독자 전원에게 `message` 이벤트가 발송됩니다.
""",
    )
    @SwaggerApiResponse(responseCode = "201", description = "메시지 전송 성공")
    @SwaggerApiResponse(
        responseCode = "400",
        description = "전송 불가",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "파티가 LIVE_OPEN 상태가 아님",
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_ACTIVE","message":"현재 채팅이 활성화된 시간이 아닙니다"}}""",
                    ),
                    ExampleObject(
                        name = "실시간 파티가 아님",
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_SUPPORTED","message":"채팅을 지원하지 않는 파티입니다"}}""",
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
                        name = "인증 필요",
                        value = """{"status":401,"error":{"code":"AUTH_UNAUTHORIZED","message":"인증이 필요합니다"}}""",
                    ),
                    ExampleObject(
                        name = "만료된 토큰",
                        value = """{"status":401,"error":{"code":"AUTH_EXPIRED_TOKEN","message":"만료된 토큰입니다"}}""",
                    ),
                    ExampleObject(
                        name = "유효하지 않은 토큰",
                        value = """{"status":401,"error":{"code":"AUTH_INVALID_TOKEN","message":"유효하지 않은 토큰입니다"}}""",
                    ),
                    ExampleObject(
                        name = "JWT·참여자 토큰 미제공",
                        value = """{"status":401,"error":{"code":"UNAUTHORIZED","message":"로그인이 필요합니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "403",
        description = "해당 파티의 참여자가 아님",
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
    fun sendMessage(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse>

    @Operation(
        summary = "실시간 파티 퇴장",
        description = """
실시간 파티 채팅에서 퇴장합니다.

로그인 사용자는 `Authorization: Bearer {token}` 헤더를, 비로그인 참여자는 `X-Participant-Token: {participantToken}` 헤더를 사용합니다.
퇴장하면 현재 SSE 연결이 종료되고, 다른 구독자에게 `user-left` 이벤트가 발송됩니다.
""",
    )
    @SwaggerApiResponse(responseCode = "204", description = "퇴장 성공")
    @SwaggerApiResponse(
        responseCode = "400",
        description = "실시간 파티가 아님",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        value =
                            """{"status":400,"error":{"code":"CHAT_NOT_SUPPORTED","message":"채팅을 지원하지 않는 파티입니다"}}""",
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
                        name = "인증 필요",
                        value = """{"status":401,"error":{"code":"AUTH_UNAUTHORIZED","message":"인증이 필요합니다"}}""",
                    ),
                    ExampleObject(
                        name = "만료된 토큰",
                        value = """{"status":401,"error":{"code":"AUTH_EXPIRED_TOKEN","message":"만료된 토큰입니다"}}""",
                    ),
                    ExampleObject(
                        name = "유효하지 않은 토큰",
                        value = """{"status":401,"error":{"code":"AUTH_INVALID_TOKEN","message":"유효하지 않은 토큰입니다"}}""",
                    ),
                    ExampleObject(
                        name = "JWT·참여자 토큰 미제공",
                        value = """{"status":401,"error":{"code":"UNAUTHORIZED","message":"로그인이 필요합니다"}}""",
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "403",
        description = "해당 파티의 참여자가 아님",
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
    fun leaveParty(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    )
}
