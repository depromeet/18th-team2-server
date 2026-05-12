package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "롤링페이퍼 파티 생성 요청")
data class CreatePaperOnlyPartyRequest(
    @Schema(description = "파티 주인공 이름", example = "홍길동")
    val celebrantNickname: String,
    @Schema(description = "파티 시작일", example = "2024-11-26")
    val startedDate: LocalDate,
)
