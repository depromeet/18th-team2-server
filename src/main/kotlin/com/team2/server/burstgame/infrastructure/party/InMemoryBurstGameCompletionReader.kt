package com.team2.server.burstgame.infrastructure.party

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.party.application.port.BurstGameCompletionReader
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class InMemoryBurstGameCompletionReader(
    private val sessionStore: BurstGameSessionStore,
) : BurstGameCompletionReader {
    override fun isCompleted(
        partyId: Long,
        now: LocalDateTime,
    ): Boolean {
        val session = sessionStore.findByPartyId(partyId, now) ?: return false
        return synchronized(session) {
            session.status == BurstGameRoundStatus.ENDED || session.isPastEndsAt(now)
        }
    }
}
