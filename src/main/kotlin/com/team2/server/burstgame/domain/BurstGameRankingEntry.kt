package com.team2.server.burstgame.domain

data class BurstGameRankingEntry(
    val rank: Int,
    val participantId: Long,
    val nickname: String,
    val characterId: Long?,
    val characterThumbnailImageUrl: String?,
    val role: String,
    val tapCount: Int,
)
