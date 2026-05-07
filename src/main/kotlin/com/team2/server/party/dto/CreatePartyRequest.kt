package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime

@Schema(description = "파티 생성 요청")
data class CreatePartyRequest(
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String,
    @Schema(description = "파티 시작일", example = "2024-11-26")
    val startedDate: LocalDate,
    @Schema(description = "파티 시작 시간 (HH:mm). 미전송 시 00:00으로 설정됩니다.", example = "14:30")
    @JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime? = null,
    @Schema(description = "캐릭터 ID (REALTIME 파티 필수)", example = "1")
    val characterId: Long? = null,
) {
    fun requireCharacterId(): Long = characterId ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
}
