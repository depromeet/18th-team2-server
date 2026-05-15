package com.team2.server.burstgame.domain

data class BurstGameWinner(
    val participantId: Long,
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val role: String,
    val tapCount: Int,
)
