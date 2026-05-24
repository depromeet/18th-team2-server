package com.team2.server.burstgame.application.port

import com.team2.server.burstgame.domain.candle.CandleBlowSession

interface CandleBlowSessionStore {
    fun <T> getOrCreateWithLock(
        partyId: Long,
        sessionFactory: () -> CandleBlowSession,
        block: (session: CandleBlowSession, created: Boolean) -> T,
    ): T

    fun <T> withSessionLock(
        partyId: Long,
        block: (CandleBlowSession) -> T,
    ): T?

    fun removeByPartyId(partyId: Long): Boolean

    fun clear()
}
