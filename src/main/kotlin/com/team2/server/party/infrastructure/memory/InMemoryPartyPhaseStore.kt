package com.team2.server.party.infrastructure.memory

import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryPartyPhaseStore : PartyPhaseStore {
    private val entries = ConcurrentHashMap<Long, PartyPhaseStore.PhaseEntry>()
    private val locks = Array(LOCK_STRIPE_COUNT) { Any() }

    override fun getEntry(partyId: Long): PartyPhaseStore.PhaseEntry? = entries[partyId]

    override fun advance(
        partyId: Long,
        from: PartyPhase,
        to: PartyPhase,
        now: LocalDateTime,
    ): Boolean =
        advance(partyId, from, to, now) {
        }

    override fun advance(
        partyId: Long,
        from: PartyPhase,
        to: PartyPhase,
        now: LocalDateTime,
        beforeUpdate: () -> Unit,
    ): Boolean {
        val lock = lockFor(partyId)
        return synchronized(lock) {
            val current = entries[partyId]?.phase ?: PartyPhase.ENTRY
            if (current != from) return@synchronized false
            beforeUpdate()
            entries[partyId] = PartyPhaseStore.PhaseEntry(phase = to, startedAt = now)
            true
        }
    }

    override fun forceSet(
        partyId: Long,
        phase: PartyPhase,
        now: LocalDateTime,
    ) {
        synchronized(lockFor(partyId)) {
            entries[partyId] = PartyPhaseStore.PhaseEntry(phase = phase, startedAt = now)
        }
    }

    override fun removeByPartyId(partyId: Long) {
        synchronized(lockFor(partyId)) {
            entries.remove(partyId)
        }
    }

    override fun clear() {
        entries.clear()
    }

    private fun lockFor(partyId: Long): Any = locks[Math.floorMod(partyId.hashCode(), LOCK_STRIPE_COUNT)]

    companion object {
        private const val LOCK_STRIPE_COUNT = 64
    }
}
