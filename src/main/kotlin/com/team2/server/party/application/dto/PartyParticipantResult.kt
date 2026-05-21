package com.team2.server.party.application.dto

data class PartyParticipantResult(
    val participantId: Long,
    val joinOrder: Int,
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val isOwner: Boolean,
    val isCelebrant: Boolean,
    val isMe: Boolean,
)
