package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResolveLiveOpenRealtimePartyUseCase(
    private val resolveRealtimePartyUseCase: ResolveRealtimePartyUseCase,
) {
    @Transactional(readOnly = true)
    fun invoke(partyId: Long): RealtimeParty {
        val party = resolveRealtimePartyUseCase.invoke(partyId)
        if (party.status() != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
        return party
    }
}
