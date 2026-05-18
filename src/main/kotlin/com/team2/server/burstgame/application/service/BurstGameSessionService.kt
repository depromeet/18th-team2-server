package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGamePolicy
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSession
import com.team2.server.burstgame.domain.BurstGameSnapshot
import com.team2.server.burstgame.domain.BurstGameTapIgnoredReason
import com.team2.server.burstgame.domain.BurstGameTapResult
import com.team2.server.burstgame.infrastructure.memory.InMemoryBurstGameSessionStore
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class BurstGameSessionService(
    private val sessionStore: InMemoryBurstGameSessionStore,
    private val candleBlowStatusReader: CandleBlowStatusReader,
) {
    data class StartResult(
        val snapshot: BurstGameSnapshot,
        val created: Boolean,
    )

    data class SnapshotResult(
        val snapshot: BurstGameSnapshot,
        val endedNow: Boolean,
    )

    fun start(
        partyId: Long,
        participant: BurstGameParticipantInfo,
        now: LocalDateTime,
    ): StartResult {
        val result =
            sessionStore.start(partyId, now) {
                validateCandleBlowCompleted(partyId)
                BurstGameSession(
                    roundId = UUID.randomUUID().toString(),
                    partyId = partyId,
                    startedAt = now,
                    endsAt = now.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
                )
            }
        val session =
            when (result) {
                is InMemoryBurstGameSessionStore.StartResult.Created -> result.session
                is InMemoryBurstGameSessionStore.StartResult.Existing -> result.session
            }

        return synchronized(session) {
            if (session.status == BurstGameRoundStatus.ENDED || session.isPastEndsAt(now)) {
                session.end(now)
                throw BusinessException(ErrorCode.BURST_GAME_ALREADY_ENDED)
            }

            StartResult(
                snapshot = session.snapshotFor(participant.participantId, now),
                created = result is InMemoryBurstGameSessionStore.StartResult.Created,
            )
        }
    }

    fun submit(
        roundId: String,
        participant: BurstGameParticipantInfo,
        tapCount: Int,
        clientSequence: Long,
        now: LocalDateTime,
    ): BurstGameTapResult {
        val session =
            sessionStore.findByRoundId(roundId, now) ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
        return synchronized(session) {
            if (session.isPastEndsAt(now)) {
                val endedNow = session.status == BurstGameRoundStatus.ACTIVE
                session.end(now)
                val snapshot = session.snapshotFor(participant.participantId, now)
                return@synchronized BurstGameTapResult(
                    accepted = false,
                    ignoredReason = BurstGameTapIgnoredReason.ROUND_ENDED,
                    snapshot = snapshot,
                    endedNow = endedNow,
                )
            }
            session.applyTap(participant, tapCount, clientSequence, now)
        }
    }

    fun snapshot(
        partyId: Long,
        participant: BurstGameParticipantInfo,
        now: LocalDateTime,
    ): SnapshotResult {
        val session =
            sessionStore.findByPartyId(partyId, now) ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
        return synchronized(session) {
            val endedNow = session.status == BurstGameRoundStatus.ACTIVE && session.isPastEndsAt(now)
            if (endedNow) {
                session.end(now)
            }
            SnapshotResult(
                snapshot = session.snapshotFor(participant.participantId, now),
                endedNow = endedNow,
            )
        }
    }

    fun findPartyIdByRoundId(
        roundId: String,
        now: LocalDateTime,
    ): Long {
        val session =
            sessionStore.findByRoundId(roundId, now) ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
        return session.partyId
    }

    fun end(
        roundId: String,
        now: LocalDateTime,
    ): SnapshotResult? {
        val session = sessionStore.findByRoundId(roundId, now) ?: return null
        return synchronized(session) {
            val endedNow = session.status == BurstGameRoundStatus.ACTIVE
            val snapshot = session.end(now)
            SnapshotResult(snapshot = snapshot, endedNow = endedNow)
        }
    }

    private fun validateCandleBlowCompleted(partyId: Long) {
        if (!candleBlowStatusReader.isCandleBlowCompleted(partyId)) {
            throw BusinessException(ErrorCode.BURST_GAME_NOT_READY)
        }
    }
}
