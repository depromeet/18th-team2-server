package com.team2.server.party.application.dto

data class PartyParticipantsResult(
    val totalCount: Int,
    val maxCount: Int,
    val participants: List<PartyParticipantResult>,
)
