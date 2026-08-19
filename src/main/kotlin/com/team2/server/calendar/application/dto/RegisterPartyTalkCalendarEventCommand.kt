package com.team2.server.calendar.application.dto

data class RegisterPartyTalkCalendarEventCommand(
    val partyId: Long,
    val userId: Long,
    val kakaoAccessToken: String,
)
