package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class BurstGameStateResponse(
    val partyId: Long,
    val myParticipantId: Long,
    @Schema(description = "박터뜨리기 라운드 종료 여부입니다.", example = "false")
    val ended: Boolean,
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
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                ended = snapshot.status.isEnded(),
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

        private fun com.team2.server.burstgame.domain.BurstGameRoundStatus.isEnded(): Boolean =
            this == com.team2.server.burstgame.domain.BurstGameRoundStatus.ENDED
    }
}
