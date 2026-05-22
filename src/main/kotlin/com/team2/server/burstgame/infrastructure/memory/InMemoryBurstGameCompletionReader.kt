package com.team2.server.burstgame.infrastructure.memory

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.party.application.port.BurstGameCompletionReader
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

@Component
class InMemoryBurstGameCompletionReader(
    private val sessionStore: BurstGameSessionStore,
    private val clock: Clock,
) : BurstGameCompletionReader {
    override fun isEnded(partyId: Long): Boolean {
        val now = LocalDateTime.now(clock)
        val session = sessionStore.findByPartyId(partyId, now) ?: return false
        return session.status == BurstGameRoundStatus.ENDED || session.isPastEndsAt(now)
    }
}
