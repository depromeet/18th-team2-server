package com.team2.server.burstgame.application.usecase

import com.team2.server.burstgame.application.dto.CandleBlowResponse
import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.support.BurstGameParticipantResolver
import com.team2.server.burstgame.application.support.CandleBlowUpdateEventPublisher
import com.team2.server.burstgame.config.CandleBlowProperties
import com.team2.server.burstgame.domain.candle.CandleBlowSession
import com.team2.server.burstgame.domain.candle.CandleBlowStatus
import com.team2.server.burstgame.domain.candle.CandleBlowUpdateResult
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class BlowCandleUseCase(
    private val participantResolver: BurstGameParticipantResolver,
    private val sessionStore: CandleBlowSessionStore,
    private val updateEventPublisher: CandleBlowUpdateEventPublisher,
    private val candleBlowProperties: CandleBlowProperties,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        candleId: Int,
        userId: Long?,
        participantToken: String?,
    ): CandleBlowResponse {
        val context = participantResolver.resolveWithParty(partyId, userId, participantToken)
        val now = LocalDateTime.now(clock)
        val existingResponse =
            sessionStore.withSessionLock(partyId) { session ->
                CandleBlowResponse.from(blowAndPublish(session, candleId, now).snapshot)
            }
        if (existingResponse != null) return existingResponse
        val hostEnteredAt =
            context.party.hostEnteredAt
                ?: throw BusinessException(ErrorCode.CANDLE_BLOW_NOT_STARTED)
        return sessionStore.getOrCreateWithLock(
            partyId = partyId,
            sessionFactory = {
                CandleBlowSession.fromHostEnteredAt(
                    partyId = partyId,
                    hostEnteredAt = hostEnteredAt,
                    durationSeconds = candleBlowProperties.durationSeconds,
                )
            },
        ) { session, _ ->
            CandleBlowResponse.from(blowAndPublish(session, candleId, now).snapshot)
        }
    }

    private fun blowAndPublish(
        session: CandleBlowSession,
        candleId: Int,
        now: LocalDateTime,
    ): CandleBlowUpdateResult {
        val result = session.blow(candleId, now)
        updateEventPublisher.publish(
            changed = result.changed,
            shouldBroadcastEnded =
                result.finishedNow &&
                    session.markAndCheckBroadcastNeeded(CandleBlowStatus.FINISHED),
            snapshot = result.snapshot,
        )
        return result
    }
}
