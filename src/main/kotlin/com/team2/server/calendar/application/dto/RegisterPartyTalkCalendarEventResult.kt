package com.team2.server.calendar.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "톡캘린더 일정 등록 결과")
data class RegisterPartyTalkCalendarEventResult(
    @Schema(description = "카카오 톡캘린더 일정 ID", example = "63630868d89d8b4150bbb712")
    val eventId: String,
    @Schema(description = "기존 일정을 갱신했으면 true, 새로 만들었으면 false", example = "false")
    val updated: Boolean,
)
