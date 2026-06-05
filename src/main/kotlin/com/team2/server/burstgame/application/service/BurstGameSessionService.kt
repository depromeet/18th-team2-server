package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.application.dto.BurstGameSnapshotResult
import com.team2.server.burstgame.application.dto.BurstGameStartResult
import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.application.port.CandleBlowStatusReader
import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSession
import com.team2.server.burstgame.domain.BurstGameTapIgnoredReason
import com.team2.server.burstgame.domain.BurstGameTapResult
import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BurstGameSessionService(
    private val sessionStore: BurstGameSessionStore,
    private val candleBlowStatusReader: CandleBlowStatusReader,
) {
    fun start(
        partyId: Long,
        hostEnteredAt: LocalDateTime?,
        participant: BurstGameParticipantInfo,
        now: LocalDateTime,
    ): BurstGameStartResult {
        val result =
            sessionStore.start(partyId, now) {
                validateCandleBlowFinished(partyId, hostEnteredAt, now)
                val startedAt = now.plusSeconds(BurstGamePolicy.COUNTDOWN_DURATION_SECONDS)
                BurstGameSession(
                    partyId = partyId,
                    startedAt = startedAt,
                    endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
                )
            }
        val session =
            when (result) {
                is BurstGameSessionStore.StartResult.Created -> result.session
                is BurstGameSessionStore.StartResult.Existing -> result.session
            }

        return synchronized(session) {
            if (session.status == BurstGameRoundStatus.ENDED || session.isPastEndsAt(now)) {
                val endedNow = session.status == BurstGameRoundStatus.ACTIVE
                session.end(now)
                return@synchronized BurstGameStartResult.AlreadyEnded(
                    snapshot = session.snapshotFor(participant.participantId, now),
                    endedNow = endedNow,
                )
            }

            BurstGameStartResult.Started(
                snapshot = session.snapshotFor(participant.participantId, now),
                created = result is BurstGameSessionStore.StartResult.Created,
            )
        }
    }

    fun submit(
        partyId: Long,
        participant: BurstGameParticipantInfo,
        tapCount: Int,
        clientSequence: Long,
        now: LocalDateTime,
    ): BurstGameTapResult {
        val session =
            sessionStore.findByPartyId(partyId, now) ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
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
    ): BurstGameSnapshotResult {
        val session =
            sessionStore.findByPartyId(partyId, now) ?: throw BusinessException(ErrorCode.BURST_GAME_NOT_FOUND)
        return synchronized(session) {
            val endedNow = session.status == BurstGameRoundStatus.ACTIVE && session.isPastEndsAt(now)
            if (endedNow) {
                session.end(now)
            }
            BurstGameSnapshotResult(
                snapshot = session.snapshotFor(participant.participantId, now),
                endedNow = endedNow,
            )
        }
    }

    fun end(
        partyId: Long,
        now: LocalDateTime,
    ): BurstGameSnapshotResult? {
        val session = sessionStore.findByPartyId(partyId, now) ?: return null
        return synchronized(session) {
            val endedNow = session.status == BurstGameRoundStatus.ACTIVE
            val snapshot = session.end(now)
            BurstGameSnapshotResult(snapshot = snapshot, endedNow = endedNow)
        }
    }

    fun removeStarted(
        partyId: Long,
        startedAt: LocalDateTime,
        now: LocalDateTime,
    ): Boolean {
        val session = sessionStore.findByPartyId(partyId, now) ?: return false
        return synchronized(session) {
            check(session.status == BurstGameRoundStatus.ACTIVE && session.startedAt == startedAt) {
                "Only the just-started active burst game session can be removed. partyId=$partyId"
            }
            sessionStore.removeByPartyId(partyId)
        }
    }

    private fun validateCandleBlowFinished(
        partyId: Long,
        hostEnteredAt: LocalDateTime?,
        now: LocalDateTime,
    ) {
        if (!candleBlowStatusReader.isCandleBlowFinished(partyId, hostEnteredAt, now)) {
            throw BusinessException(ErrorCode.BURST_GAME_NOT_READY)
        }
    }
}
