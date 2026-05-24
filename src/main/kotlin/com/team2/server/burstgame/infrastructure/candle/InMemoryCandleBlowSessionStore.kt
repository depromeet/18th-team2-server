package com.team2.server.burstgame.infrastructure.candle

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryCandleBlowSessionStore : CandleBlowSessionStore {
    private val sessionsByPartyId = ConcurrentHashMap<Long, CandleBlowSession>()
    private val partyLocks = Array(LOCK_STRIPE_COUNT) { Any() }

    override fun getOrCreate(
        partyId: Long,
        sessionFactory: () -> CandleBlowSession,
    ): CandleBlowSessionStore.CreateResult {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            val existing = sessionsByPartyId[partyId]
            if (existing != null) {
                CandleBlowSessionStore.CreateResult.Existing(existing)
            } else {
                val session = sessionFactory()
                require(session.partyId == partyId) {
                    "Candle blow session partyId mismatch. expected=$partyId actual=${session.partyId}"
                }
                sessionsByPartyId[partyId] = session
                CandleBlowSessionStore.CreateResult.Created(session)
            }
        }
    }

    override fun findByPartyId(partyId: Long): CandleBlowSession? {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            sessionsByPartyId[partyId]
        }
    }

    override fun removeByPartyId(partyId: Long): Boolean {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            sessionsByPartyId.remove(partyId) != null
        }
    }

    override fun clear() {
        sessionsByPartyId.clear()
    }

    private fun lockFor(partyId: Long): Any = partyLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    private companion object {
        const val LOCK_STRIPE_COUNT = 64
    }
}
