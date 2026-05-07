package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Chat", description = "실시간 채팅 API")
interface ChatApi {
    @Operation(summary = "실시간 파티 입장 (비로그인 포함)")
    @SwaggerApiResponse(responseCode = "201", description = "입장 성공, participantToken 반환")
    @InternalServerErrorResponse
    fun enterRealtimeParty(
        @Parameter(description = "초대 토큰") inviteToken: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
        request: EnterRealtimePartyRequest,
    ): ApiResponse<EnterRealtimePartyResponse>

    @Operation(summary = "채팅 메시지 전송")
    @SwaggerApiResponse(responseCode = "201", description = "메시지 전송 성공")
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun sendMessage(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse>

    @Operation(
        summary = "채팅 메시지 SSE 구독",
        description = """
SSE(Server-Sent Events) 방식으로 실시간 채팅 스트림에 연결합니다.

**⚠️ Swagger UI에서는 직접 실행이 불가합니다.** 아래 curl 명령어를 사용하세요.

---

**연결 즉시 수신되는 이벤트**

| 이벤트 이름 | 설명 |
|---|---|
| `history` | 기존 메시지 전체 목록 (배열) |

**이후 실시간으로 수신되는 이벤트**

| 이벤트 이름 | 설명 |
|---|---|
| `message` | 새로 전송된 메시지 1건 |

---

**이벤트 데이터 구조 (history / message 공통)**
```json
{
  "messageId": 1,
  "content": "안녕하세요!",
  "senderNickname": "닉네임",
  "senderCharacterId": 1,
  "sentAt": "2026-05-07T10:00:00"
}
```

---

**curl 예시 (비로그인)**
```
curl -N http://localhost:8080/api/v1/parties/{partyId}/chat-messages/stream \
  -H "X-Participant-Token: {participantToken}"
```

**curl 예시 (로그인)**
```
curl -N http://localhost:8080/api/v1/parties/{partyId}/chat-messages/stream \
  -H "Authorization: Bearer {token}"
```

연결은 **15분** 후 자동 종료됩니다.
""",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "SSE 스트림 연결 성공",
        content = [Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = Schema(type = "string"))],
    )
    @AuthErrorResponses
    @InternalServerErrorResponse
    fun subscribe(
        @Parameter(description = "파티 ID") partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        @Parameter(description = "비로그인 참여자 토큰", `in` = ParameterIn.HEADER, name = "X-Participant-Token")
        participantToken: String?,
    ): SseEmitter
}
