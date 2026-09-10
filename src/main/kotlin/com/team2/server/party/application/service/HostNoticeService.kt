package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 주최자에게 한 번만 노출하는 안내의 소진 상태를 다룬다. */
@Service
class HostNoticeService(
    private val partyRepository: PartyRepository,
) {
    /** 주최자가 롤링페이퍼 오픈 안내를 확인했음을 기록한다. 멱등이다. */
    fun markRollingPaperNoticeSeen(
        partyId: Long,
        userId: Long,
        now: LocalDateTime,
    ) {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (party.ownerId != userId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        party.markHostRollingPaperNoticeSeen(now)
    }
}
