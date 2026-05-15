package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameTapResult
import java.time.LocalDateTime

data class SubmitBurstGameTapResponse(
    val roundId: String,
    val myParticipantId: Long,
    val accepted: Boolean,
    val ignoredReason: String?,
    val totalTapCount: Int,
    val myTapCount: Int,
    val colorChanged: Boolean,
    val stateVersion: Long,
    val serverTime: LocalDateTime,
    val rankings: List<BurstGameRankingResponse>,
) {
    companion object {
        fun from(result: BurstGameTapResult): SubmitBurstGameTapResponse {
            val snapshot = result.snapshot
            return SubmitBurstGameTapResponse(
                roundId = snapshot.roundId,
                myParticipantId = snapshot.myParticipantId,
                accepted = result.accepted,
                ignoredReason = result.ignoredReason?.name,
                totalTapCount = snapshot.totalTapCount,
                myTapCount = snapshot.myTapCount,
                colorChanged = snapshot.colorChanged,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
                rankings = snapshot.rankings.map { BurstGameRankingResponse.from(it) },
            )
        }
    }
}
