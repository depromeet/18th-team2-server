package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.HostNoticeService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MarkHostRollingPaperNoticeSeenUseCase(
    private val hostNoticeService: HostNoticeService,
) {
    @Transactional
    fun invoke(
        partyId: Long,
        userId: Long,
    ) {
        hostNoticeService.markRollingPaperNoticeSeen(
            partyId = partyId,
            userId = userId,
            now = LocalDateTime.now(),
        )
    }
}
