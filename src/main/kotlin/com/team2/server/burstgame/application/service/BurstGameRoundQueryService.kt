package com.team2.server.burstgame.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BurstGameRoundQueryService(
    private val sessionStore: BurstGameSessionStore,
) {
    fun findPartyIdByRoundId(
        roundId: String,
        now: LocalDateTime,
    ): Long {
        val session =
            sessionStore.findByRoundId(roundId, now)
                ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
        return session.partyId
    }
}
