package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameSnapshot
import java.time.LocalDateTime

data class BurstGameSnapshotResponse(
    val roundId: String,
    val partyId: Long,
    val myParticipantId: Long,
    val status: String,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val totalTapCount: Int,
    val myTapCount: Int,
    val colorChanged: Boolean,
    val stateVersion: Long,
    val serverTime: LocalDateTime,
    val remainingSeconds: Long,
    val rankings: List<BurstGameRankingResponse>,
    val winners: List<BurstGameWinnerResponse>,
) {
    companion object {
        fun from(snapshot: BurstGameSnapshot): BurstGameSnapshotResponse =
            BurstGameSnapshotResponse(
                roundId = snapshot.roundId,
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                status = snapshot.status.name,
                startedAt = snapshot.startedAt,
                endsAt = snapshot.endsAt,
                totalTapCount = snapshot.totalTapCount,
                myTapCount = snapshot.myTapCount,
                colorChanged = snapshot.colorChanged,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
                remainingSeconds = snapshot.remainingSeconds,
                rankings = snapshot.rankings.map { BurstGameRankingResponse.from(it) },
                winners = snapshot.winners.map { BurstGameWinnerResponse.from(it) },
            )
    }
}
