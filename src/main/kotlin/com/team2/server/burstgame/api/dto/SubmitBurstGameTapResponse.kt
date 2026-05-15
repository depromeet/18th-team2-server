package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameTapIgnoredReason
import com.team2.server.burstgame.domain.BurstGameTapResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class SubmitBurstGameTapResponse(
    val roundId: String,
    val myParticipantId: Long,
    val accepted: Boolean,
    @Schema(
        description = "터치 batch가 반영되지 않은 이유입니다. accepted=true이면 null입니다.",
        allowableValues = ["DUPLICATE_SEQUENCE", "ROUND_ENDED"],
        nullable = true,
    )
    val ignoredReason: BurstGameTapIgnoredReason?,
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
                ignoredReason = result.ignoredReason,
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
