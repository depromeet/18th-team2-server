package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class BurstGameStateResponse(
    val roundId: String,
    val partyId: Long,
    val myParticipantId: Long,
    val status: BurstGameRoundStatus,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val totalTapCount: Int,
    val myTapCount: Int,
    val colorChanged: Boolean,
    val stateVersion: Long,
    val serverTime: LocalDateTime,
    val remainingSeconds: Long,
    @Schema(description = "진행 중 상태에서만 제공되는 상위 3개 rank group입니다. 종료 상태에서는 비어 있습니다.")
    val rankings: List<BurstGameRankingResponse>,
    @Schema(description = "종료 상태에서만 제공되는 공동 1등 목록입니다. 진행 중에는 비어 있습니다.")
    val winners: List<BurstGameWinnerResponse>,
) {
    companion object {
        fun from(snapshot: BurstGameSnapshot): BurstGameStateResponse =
            BurstGameStateResponse(
                roundId = snapshot.roundId,
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                status = snapshot.status,
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
