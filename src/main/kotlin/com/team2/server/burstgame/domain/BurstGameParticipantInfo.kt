package com.team2.server.burstgame.domain

data class BurstGameParticipantInfo(
    val participantId: Long,
    val nickname: String,
    val characterId: Long?,
    val characterThumbnailImageUrl: String?,
    val role: String,
)
