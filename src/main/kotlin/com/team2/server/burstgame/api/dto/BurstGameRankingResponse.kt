package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameRankingEntry
import com.team2.server.burstgame.domain.BurstGameWinner

data class BurstGameRankingResponse(
    val rank: Int,
    val participantId: Long,
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val role: String,
    val tapCount: Int,
) {
    companion object {
        fun from(entry: BurstGameRankingEntry): BurstGameRankingResponse =
            BurstGameRankingResponse(
                rank = entry.rank,
                participantId = entry.participantId,
                nickname = entry.nickname,
                characterId = entry.characterId,
                characterImageUrl = entry.characterImageUrl,
                role = entry.role,
                tapCount = entry.tapCount,
            )
    }
}

data class BurstGameWinnerResponse(
    val participantId: Long,
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val role: String,
    val tapCount: Int,
) {
    companion object {
        fun from(winner: BurstGameWinner): BurstGameWinnerResponse =
            BurstGameWinnerResponse(
                participantId = winner.participantId,
                nickname = winner.nickname,
                characterId = winner.characterId,
                characterImageUrl = winner.characterImageUrl,
                role = winner.role,
                tapCount = winner.tapCount,
            )
    }
}
