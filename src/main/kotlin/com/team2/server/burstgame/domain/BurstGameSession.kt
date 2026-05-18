package com.team2.server.burstgame.domain

import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import com.team2.server.burstgame.domain.policy.BurstGameRankingPolicy
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import java.time.Duration
import java.time.LocalDateTime

class BurstGameSession(
    val partyId: Long,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
) {
    init {
        require(endsAt.isAfter(startedAt)) {
            "Burst game endsAt must be after startedAt. startedAt=$startedAt endsAt=$endsAt"
        }
    }

    var status: BurstGameRoundStatus = BurstGameRoundStatus.ACTIVE
        private set
    var stateVersion: Long = 0
        private set
    var endedAt: LocalDateTime? = null
        private set

    private val participantScores = mutableMapOf<Long, ParticipantScore>()

    fun applyTap(
        participant: BurstGameParticipantInfo,
        tapCount: Int,
        clientSequence: Long,
        now: LocalDateTime,
    ): BurstGameTapResult {
        validateTapInput(tapCount, clientSequence)
        return when {
            status == BurstGameRoundStatus.ENDED || !now.isBefore(endsAt) ->
                BurstGameTapResult(
                    accepted = false,
                    ignoredReason = BurstGameTapIgnoredReason.ROUND_ENDED,
                    snapshot = snapshotFor(participant.participantId, now),
                )

            else -> applyActiveTap(participant, tapCount, clientSequence, now)
        }
    }

    private fun applyActiveTap(
        participant: BurstGameParticipantInfo,
        tapCount: Int,
        clientSequence: Long,
        now: LocalDateTime,
    ): BurstGameTapResult {
        val score =
            participantScores.getOrPut(participant.participantId) {
                ParticipantScore(participant = participant, rateLimiter = TapRateLimiter(startedAt))
            }
        score.participant = participant

        return if (clientSequence in score.processedSequences) {
            BurstGameTapResult(
                accepted = false,
                ignoredReason = BurstGameTapIgnoredReason.DUPLICATE_SEQUENCE,
                snapshot = snapshotFor(participant.participantId, now),
            )
        } else {
            applyNewTap(score, tapCount, clientSequence, participant.participantId, now)
        }
    }

    private fun applyNewTap(
        score: ParticipantScore,
        tapCount: Int,
        clientSequence: Long,
        participantId: Long,
        now: LocalDateTime,
    ): BurstGameTapResult {
        validateSequenceGap(clientSequence, score.maxAcceptedSequence)
        validateRoundTapLimit(score.tapCount, tapCount)
        if (!score.rateLimiter.tryConsume(tapCount, now)) {
            throw BusinessException(ErrorCode.BURST_GAME_RATE_LIMITED)
        }

        score.processedSequences.add(clientSequence)
        score.maxAcceptedSequence = maxOf(score.maxAcceptedSequence, clientSequence)
        score.tapCount += tapCount
        stateVersion += 1

        return BurstGameTapResult(
            accepted = true,
            ignoredReason = null,
            snapshot = snapshotFor(participantId, now),
        )
    }

    fun end(now: LocalDateTime): BurstGameSnapshot {
        if (status == BurstGameRoundStatus.ACTIVE) {
            status = BurstGameRoundStatus.ENDED
            endedAt = now
            stateVersion += 1
        }
        return snapshotFor(myParticipantId = 0L, now = now)
    }

    fun snapshotFor(
        myParticipantId: Long,
        now: LocalDateTime,
    ): BurstGameSnapshot {
        val scores = participantScores.values.map { it.toRankingScore() }
        val totalTapCount = participantScores.values.sumOf { it.tapCount }
        return BurstGameSnapshot(
            partyId = partyId,
            myParticipantId = myParticipantId,
            status = status,
            startedAt = startedAt,
            endsAt = endsAt,
            totalTapCount = totalTapCount,
            myTapCount = participantScores[myParticipantId]?.tapCount ?: 0,
            colorChanged = totalTapCount >= BurstGamePolicy.COLOR_CHANGE_TAP_COUNT,
            stateVersion = stateVersion,
            serverTime = now,
            remainingSeconds = BurstGameSnapshot.remainingSeconds(endsAt, now),
            rankings =
                if (status == BurstGameRoundStatus.ACTIVE) {
                    BurstGameRankingPolicy.rankings(scores)
                } else {
                    emptyList()
                },
            winners = if (status == BurstGameRoundStatus.ENDED) BurstGameRankingPolicy.winners(scores) else emptyList(),
        )
    }

    fun isExpired(now: LocalDateTime): Boolean {
        val endedAt = endedAt ?: return false
        return !now.isBefore(endedAt.plus(BurstGamePolicy.ENDED_SESSION_TTL))
    }

    fun isPastEndsAt(now: LocalDateTime): Boolean = !now.isBefore(endsAt)

    private fun validateTapInput(
        tapCount: Int,
        clientSequence: Long,
    ) {
        if (tapCount <= 0 || clientSequence <= 0) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun validateSequenceGap(
        clientSequence: Long,
        maxAcceptedSequence: Long,
    ) {
        if (clientSequence > maxAcceptedSequence + BurstGamePolicy.MAX_SEQUENCE_GAP) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun validateRoundTapLimit(
        currentTapCount: Int,
        tapCount: Int,
    ) {
        if (currentTapCount + tapCount > BurstGamePolicy.MAX_PARTICIPANT_ROUND_TAP_COUNT) {
            throw BusinessException(ErrorCode.BURST_GAME_RATE_LIMITED)
        }
    }

    private data class ParticipantScore(
        var participant: BurstGameParticipantInfo,
        var tapCount: Int = 0,
        var maxAcceptedSequence: Long = 0,
        val processedSequences: MutableSet<Long> = mutableSetOf(),
        val rateLimiter: TapRateLimiter,
    ) {
        fun toRankingScore(): BurstGameRankingPolicy.Score =
            BurstGameRankingPolicy.Score(participant = participant, tapCount = tapCount)
    }

    private class TapRateLimiter(
        startedAt: LocalDateTime,
    ) {
        private var availableTokens = BurstGamePolicy.TOKEN_BUCKET_CAPACITY.toDouble()
        private var lastRefillAt = startedAt

        fun tryConsume(
            tapCount: Int,
            now: LocalDateTime,
        ): Boolean {
            refill(now)
            if (availableTokens < tapCount) return false
            availableTokens -= tapCount
            return true
        }

        private fun refill(now: LocalDateTime) {
            if (now.isBefore(lastRefillAt)) return
            val elapsedSeconds = Duration.between(lastRefillAt, now).toNanos() / NANOS_PER_SECOND
            availableTokens =
                minOf(
                    BurstGamePolicy.TOKEN_BUCKET_CAPACITY.toDouble(),
                    availableTokens + elapsedSeconds * BurstGamePolicy.TOKEN_REFILL_PER_SECOND,
                )
            lastRefillAt = now
        }
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
