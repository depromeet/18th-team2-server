package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameSnapshot
import java.time.LocalDateTime

data class StartBurstGameResponse(
    val roundId: String,
    val partyId: Long,
    val myParticipantId: Long,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val totalTapCount: Int,
    val colorChanged: Boolean,
    val stateVersion: Long,
    val serverTime: LocalDateTime,
) {
    companion object {
        fun from(snapshot: BurstGameSnapshot): StartBurstGameResponse =
            StartBurstGameResponse(
                roundId = snapshot.roundId,
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                startedAt = snapshot.startedAt,
                endsAt = snapshot.endsAt,
                totalTapCount = snapshot.totalTapCount,
                colorChanged = snapshot.colorChanged,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
            )
    }
}
