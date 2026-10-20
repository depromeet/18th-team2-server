package com.team2.server.party.application.usecase

import com.team2.server.party.application.port.RealtimePartyEventBroadcaster
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyDeletedReason
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class DeletePartyUseCase(
    private val partyService: PartyService,
    private val eventBroadcasters: List<RealtimePartyEventBroadcaster>,
    private val clock: Clock,
) {
    /**
     * 실시간 파티는 입장 대기 중인 참가자가 있을 수 있으므로 삭제 사실을 브로드캐스트한다.
     * WebSocket 구현은 커밋 이후에 전송하므로, 삭제가 롤백되면 이벤트도 나가지 않는다.
     */
    @Transactional
    fun delete(
        partyId: Long,
        userId: Long,
    ) {
        val party = partyService.deleteParty(partyId = partyId, userId = userId)
        if (party is RealtimeParty) {
            val now = LocalDateTime.now(clock)
            eventBroadcasters.forEach { it.broadcastPartyDeleted(partyId, PartyDeletedReason.HOST_DELETED, now) }
        }
    }
}
