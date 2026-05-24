package com.team2.server.burstgame.domain.candle

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import java.time.LocalDateTime
import java.util.TreeSet

class CandleBlowSession(
    val partyId: Long,
    val startedAt: LocalDateTime,
    val endsAt: LocalDateTime,
) {
    init {
        require(endsAt.isAfter(startedAt)) {
            "Candle blow endsAt must be after startedAt. startedAt=$startedAt endsAt=$endsAt"
        }
    }

    var finishedReason: CandleBlowFinishedReason? = null
        private set

    private val extinguishedCandleIds = TreeSet<Int>()

    fun blow(
        candleId: Int,
        now: LocalDateTime,
    ): CandleBlowUpdateResult {
        CandleBlowPolicy.validateCandleId(candleId)
        val timedOutNow = finishIfTimedOut(now)
        if (timedOutNow || isFinished()) {
            return unchanged(now, timedOutNow)
        }
        if (now.isBefore(startedAt)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        if (candleId in extinguishedCandleIds) {
            return unchanged(now)
        }

        extinguishedCandleIds.add(candleId)
        val finishedNow = finishIfAllExtinguished(now)

        return CandleBlowUpdateResult(
            changed = true,
            finishedNow = finishedNow,
            snapshot = snapshot(now),
        )
    }

    fun finishIfTimedOut(now: LocalDateTime): Boolean {
        if (isFinished() || now.isBefore(endsAt)) return false
        finish(CandleBlowFinishedReason.TIMEOUT)
        return true
    }

    fun snapshot(now: LocalDateTime): CandleBlowSnapshot =
        CandleBlowSnapshot(
            partyId = partyId,
            status = status(now),
            candles =
                (1..CandleBlowPolicy.CANDLE_COUNT).map { candleId ->
                    CandleState(
                        candleId = candleId,
                        extinguished = candleId in extinguishedCandleIds,
                    )
                },
            remainingCount = CandleBlowPolicy.CANDLE_COUNT - extinguishedCandleIds.size,
            finishedReason = finishedReason,
        )

    fun isFinished(): Boolean = finishedReason != null

    fun status(now: LocalDateTime): CandleBlowStatus =
        when {
            isFinished() -> CandleBlowStatus.FINISHED
            now.isBefore(startedAt) -> CandleBlowStatus.WAITING
            else -> CandleBlowStatus.ACTIVE
        }

    private fun finishIfAllExtinguished(now: LocalDateTime): Boolean {
        if (extinguishedCandleIds.size != CandleBlowPolicy.CANDLE_COUNT) return false
        finish(CandleBlowFinishedReason.ALL_EXTINGUISHED)
        return true
    }

    private fun finish(reason: CandleBlowFinishedReason) {
        if (isFinished()) return
        this.finishedReason = reason
    }

    private fun unchanged(
        now: LocalDateTime,
        finishedNow: Boolean = false,
    ): CandleBlowUpdateResult =
        CandleBlowUpdateResult(
            changed = false,
            finishedNow = finishedNow,
            snapshot = snapshot(now),
        )

    companion object {
        fun fromPartyStartedAt(
            partyId: Long,
            partyStartedAt: LocalDateTime,
        ): CandleBlowSession {
            val startedAt = partyStartedAt.plusSeconds(CandleBlowPolicy.START_DELAY_SECONDS)
            return CandleBlowSession(
                partyId = partyId,
                startedAt = startedAt,
                endsAt = startedAt.plusSeconds(CandleBlowPolicy.DURATION_SECONDS),
            )
        }
    }
}
