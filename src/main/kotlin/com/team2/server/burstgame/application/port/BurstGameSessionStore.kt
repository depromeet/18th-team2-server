package com.team2.server.burstgame.application.port

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

    fun removeExpired(now: LocalDateTime)

    fun removeByPartyId(partyId: Long): Boolean

    fun clear()

    sealed interface StartResult {
        data class Created(
            val session: BurstGameSession,
        ) : StartResult

        data class Existing(
            val session: BurstGameSession,
        ) : StartResult
    }
}
