package com.team2.server.party.application.usecase

import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.RealtimePartyEndResult
import com.team2.server.party.application.dto.RealtimePartyEndStartResult
import com.team2.server.party.application.service.PartyQueryService
import com.team2.server.party.application.service.RealtimePartyEndResultService
import com.team2.server.party.application.service.RealtimePartyEndService
import com.team2.server.party.domain.entity.RealtimePartyEndTrigger
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class StartRealtimePartyEndUseCase(
    private val partyQueryService: PartyQueryService,
    private val realtimePartyEndService: RealtimePartyEndService,
    private val endResultService: RealtimePartyEndResultService,
    private val clock: Clock,
) {
    /**
     * 종료 카운트다운을 시작한다. 이미 시작됐으면 저장된 원인을 그대로 돌려준다(조건부 UPDATE 로 최초 1회만 기록).
     * 따라서 명시 종료 직후 들어온 주최자 퇴장은 HOST_REQUEST 를 HOST_LEFT 로 덮어쓰지 않는다.
     */
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long,
        trigger: RealtimePartyEndTrigger = RealtimePartyEndTrigger.HOST_REQUEST,
    ): RealtimePartyEndResult {
        val now = LocalDateTime.now(clock)
        val party = partyQueryService.requireRealtimeParty(partyId)
        if (party.ownerId != userId) throwPartyBusiness(ErrorCode.PARTY_FORBIDDEN)
        return when (party.status(now)) {
            RealtimePartyStatus.LIVE_CLOSED -> throwPartyBusiness(ErrorCode.REALTIME_PARTY_ALREADY_ENDED)
            RealtimePartyStatus.LIVE_ENDING ->
                toResultAndPublish(
                    realtimePartyEndService.startIfNotStarted(
                        party.id,
                        party.liveEndingStartedAt ?: party.automaticEndingStartedAt(),
                        party.endingReason(now) ?: party.endingReasonForHost(trigger, now),
                    ),
                    now,
                )
            RealtimePartyStatus.LIVE_OPEN ->
                toResultAndPublish(
                    realtimePartyEndService.startIfNotStarted(
                        party.id,
                        now,
                        party.endingReasonForHost(trigger, now),
                    ),
                    now,
                )
            else -> throwPartyBusiness(ErrorCode.REALTIME_PARTY_INVALID_STATE)
        }
    }

    private fun toResultAndPublish(
        startResult: RealtimePartyEndStartResult,
        now: LocalDateTime,
    ) = endResultService.toResultAndPublish(startResult, now)
}
