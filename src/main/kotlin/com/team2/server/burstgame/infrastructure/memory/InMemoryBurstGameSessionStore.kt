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
    private val sessionsByRoundId = ConcurrentHashMap<String, BurstGameSession>()
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
                require(sessionsByRoundId.putIfAbsent(session.roundId, session) == null) {
                    "Burst game round already exists. roundId=${session.roundId}"
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

    override fun findByRoundId(
        roundId: String,
        now: LocalDateTime,
    ): BurstGameSession? {
        val session = sessionsByRoundId[roundId] ?: return null
        val lock = lockFor(session.partyId)
        return synchronized(lock) {
            pruneExpiredLocked(session.partyId, now)
            sessionsByRoundId[roundId]
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

    override fun removeByRoundId(roundId: String): Boolean {
        val session = sessionsByRoundId[roundId] ?: return false
        val lock = lockFor(session.partyId)
        return synchronized(lock) {
            val current = sessionsByRoundId[roundId] ?: return@synchronized false
            sessionsByRoundId.remove(roundId, current)
            sessionsByPartyId.remove(current.partyId, current)
        }
    }

    private fun pruneExpiredLocked(
        partyId: Long,
        now: LocalDateTime,
    ) {
        val session = sessionsByPartyId[partyId] ?: return
        if (session.status == BurstGameRoundStatus.ENDED && session.isExpired(now)) {
            sessionsByPartyId.remove(partyId, session)
            sessionsByRoundId.remove(session.roundId, session)
        }
    }

    private fun lockFor(partyId: Long): Any = partyLocks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    private companion object {
        const val LOCK_STRIPE_COUNT = 64
    }
}
