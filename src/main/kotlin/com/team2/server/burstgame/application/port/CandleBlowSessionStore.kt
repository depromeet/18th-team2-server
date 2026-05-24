package com.team2.server.burstgame.application.port

import com.team2.server.burstgame.domain.candle.CandleBlowSession

interface CandleBlowSessionStore {
    fun getOrCreate(
        partyId: Long,
        sessionFactory: () -> CandleBlowSession,
    ): CreateResult

    fun findByPartyId(partyId: Long): CandleBlowSession?

    fun removeByPartyId(partyId: Long): Boolean

    fun clear()

    sealed interface CreateResult {
        data class Created(
            val session: CandleBlowSession,
        ) : CreateResult

        data class Existing(
            val session: CandleBlowSession,
        ) : CreateResult
    }
}
