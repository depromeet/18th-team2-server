package com.team2.server.party.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "실시간 파티 입장 프로필 작성·수정 요청")
data class UpsertParticipantRealtimeProfileRequest(
    @field:NotBlank
    @field:Size(max = 10)
    @Schema(description = "파티 내 표시 닉네임. 최대 10자. trim된 값이 저장됩니다.", example = "안녕용가리")
    val nickname: String,
    @field:NotNull
    @Schema(description = "선택한 캐릭터 ID", example = "1")
    val characterId: Long?,
)
