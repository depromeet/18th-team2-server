package com.team2.server.party.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "파티 참여 요청")
data class JoinPartyRequest(
    @Schema(description = "참여자 닉네임", example = "홍길동")
    @field:NotBlank(message = "닉네임은 필수입니다")
    @field:Size(max = 20, message = "닉네임은 20자 이하여야 합니다")
    val nickname: String,
    @Schema(
        description = "캐릭터 ID. 채팅 허용 파티에서는 필수이며, 채팅 비허용 파티에서는 전달할 수 없습니다.",
        example = "1",
    )
    @field:Positive(message = "캐릭터 ID는 1 이상이어야 합니다")
    val characterId: Long?,
)
