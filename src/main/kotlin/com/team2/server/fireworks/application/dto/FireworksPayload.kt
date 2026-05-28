package com.team2.server.fireworks.application.dto

data class FireworksPayload(
    val partyId: Long,
    val participantId: Long,
    val nickname: String,
)
