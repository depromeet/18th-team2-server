package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.domain.BurstGameSession
import java.time.LocalDateTime

interface BurstGameSessionStore {
    fun start(
        partyId: Long,
        now: LocalDateTime,
        sessionFactory: () -> BurstGameSession,
    ): StartResult

    fun findByPartyId(
        partyId: Long,
        now: LocalDateTime,
    ): BurstGameSession?

    fun findByRoundId(
        roundId: String,
        now: LocalDateTime,
    ): BurstGameSession?

    fun removeExpired(now: LocalDateTime)

    fun removeByRoundId(roundId: String): Boolean

    sealed interface StartResult {
        data class Created(
            val session: BurstGameSession,
        ) : StartResult

        data class Existing(
            val session: BurstGameSession,
        ) : StartResult
    }
}
