package com.team2.server.burstgame.infrastructure.memory

import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSession
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryBurstGameSessionStore {
    private val sessionsByPartyId = ConcurrentHashMap<Long, BurstGameSession>()
    private val sessionsByRoundId = ConcurrentHashMap<String, BurstGameSession>()
    private val partyLocks = ConcurrentHashMap<Long, Any>()

    fun start(
        partyId: Long,
        now: LocalDateTime,
        sessionFactory: () -> BurstGameSession,
    ): StartResult {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            pruneExpiredLocked(partyId, now)
            val existing = sessionsByPartyId[partyId]
            if (existing != null) {
                StartResult.Existing(existing)
            } else {
                val session = sessionFactory()
                sessionsByPartyId[partyId] = session
                sessionsByRoundId[session.roundId] = session
                StartResult.Created(session)
            }
        }
    }

    fun findByPartyId(
        partyId: Long,
        now: LocalDateTime,
    ): BurstGameSession? {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            pruneExpiredLocked(partyId, now)
            sessionsByPartyId[partyId]
        }
    }

    fun findByRoundId(
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

    fun removeExpired(now: LocalDateTime) {
        sessionsByPartyId.keys.forEach { partyId ->
            val lock = lockFor(partyId)
            synchronized(lock) {
                pruneExpiredLocked(partyId, now)
            }
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

    private fun lockFor(partyId: Long): Any = partyLocks.computeIfAbsent(partyId) { Any() }

    sealed interface StartResult {
        data class Created(
            val session: BurstGameSession,
        ) : StartResult

        data class Existing(
            val session: BurstGameSession,
        ) : StartResult
    }
}
