package com.team2.server.burstgame.infrastructure.memory

import com.team2.server.burstgame.application.service.BurstGameSessionStore
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSession
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryBurstGameSessionStore : BurstGameSessionStore {
    private val sessionsByPartyId = ConcurrentHashMap<Long, BurstGameSession>()
    private val partyLocks = Array(LOCK_STRIPE_COUNT) { Any() }

    override fun start(
        partyId: Long,
        now: LocalDateTime,
        sessionFactory: () -> BurstGameSession,
    ): BurstGameSessionStore.StartResult {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            pruneExpiredLocked(partyId, now)
            val existing = sessionsByPartyId[partyId]
            if (existing != null) {
                BurstGameSessionStore.StartResult.Existing(existing)
            } else {
                val session = sessionFactory()
                require(session.partyId == partyId) {
                    "Burst game session partyId mismatch. expected=$partyId actual=${session.partyId}"
                }
                sessionsByPartyId[partyId] = session
                BurstGameSessionStore.StartResult.Created(session)
            }
        }
    }

    override fun findByPartyId(
        partyId: Long,
        now: LocalDateTime,
    ): BurstGameSession? {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            pruneExpiredLocked(partyId, now)
            sessionsByPartyId[partyId]
        }
    }

    override fun removeExpired(now: LocalDateTime) {
        sessionsByPartyId.keys.forEach { partyId ->
            val lock = lockFor(partyId)
            synchronized(lock) {
                pruneExpiredLocked(partyId, now)
            }
        }
    }

    override fun removeByPartyId(partyId: Long): Boolean {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            sessionsByPartyId.remove(partyId) != null
        }
    }

    private fun pruneExpiredLocked(
        partyId: Long,
        now: LocalDateTime,
    ) {
        val session = sessionsByPartyId[partyId] ?: return
        if (session.status == BurstGameRoundStatus.ENDED && session.isExpired(now)) {
            sessionsByPartyId.remove(partyId, session)
        }
    }

    private fun lockFor(partyId: Long): Any = partyLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    private companion object {
        const val LOCK_STRIPE_COUNT = 64
    }
}
