package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime

@Schema(description = "실시간 파티 생성 요청")
data class CreateRealtimePartyRequest(
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String,
    @Schema(description = "파티 시작일", example = "2024-11-26")
    val startedDate: LocalDate,
    @Schema(description = "파티 시작 시간 (HH:mm)", example = "14:30")
    @JsonFormat(pattern = "HH:mm")
    val startTime: LocalTime,
    @Schema(description = "캐릭터 ID", example = "1")
    val characterId: Long,
)
