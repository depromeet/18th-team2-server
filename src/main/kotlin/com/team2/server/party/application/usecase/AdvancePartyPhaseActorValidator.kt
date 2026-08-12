package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class AdvancePartyPhaseActorValidator(
    private val participantService: ParticipantService,
) {
    fun validate(
        party: RealtimeParty,
        currentPhase: PartyPhase,
        now: LocalDateTime,
        userId: Long?,
        participantToken: String?,
    ) {
        when (currentPhase) {
            PartyPhase.ENTRY -> validateEntry(party, now, userId)
            PartyPhase.MUSIC,
            PartyPhase.CANDLE,
            -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }
    }

    private fun validateEntry(
        party: RealtimeParty,
        now: LocalDateTime,
        userId: Long?,
    ) {
        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        // 라이브 창 밖에서의 시작은 거부한다. startedAt 이전이면 종료 시각이 startedAt보다 앞설 수 있고,
        // 마감선을 넘겼으면 이미 끝났어야 할 파티가 시작 시각 기록으로 되살아난다. 마킹은 1회성이라 복구 불가.
        if (!party.isLiveOpen(now)) throw BusinessException(ErrorCode.REALTIME_PARTY_INVALID_STATE)
    }
}
