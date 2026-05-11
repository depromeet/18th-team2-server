package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Party
import com.team2.server.party.repository.PartyRepository
import com.team2.server.rollingpaper.dto.OwnerRollingPaperDetailResponse
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class GetRollingPaperDetailUseCase(
    private val partyRepository: PartyRepository,
    private val rollingPaperRepository: RollingPaperRepository,
) {
    @Transactional(readOnly = true)
    fun getOwnerDetail(
        partyId: Long,
        rollingPaperId: Long,
        userId: Long,
    ): OwnerRollingPaperDetailResponse {
        val party =
            partyRepository.findByIdOrNull(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        validateOwner(party, userId)
        validateViewable(party)

        val rollingPaper =
            rollingPaperRepository.findByIdAndParty(rollingPaperId, party)
                ?: throw BusinessException(ErrorCode.ROLLING_PAPER_NOT_FOUND)
        val totalCount = rollingPaperRepository.countByParty(party)
        val position = calculatePosition(party, rollingPaper)

        return OwnerRollingPaperDetailResponse(
            rollingPaperId = rollingPaper.id,
            content = rollingPaper.content,
            writerNickname = rollingPaper.writerNickname,
            position = position,
            totalCount = totalCount,
        )
    }

    private fun validateOwner(
        party: Party,
        userId: Long,
    ) {
        if (party.ownerId != userId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }

    private fun validateViewable(party: Party) {
        if (!party.canHostViewRollingPapers(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_NOT_VIEWABLE)
        }
    }

    private fun calculatePosition(
        party: Party,
        rollingPaper: RollingPaper,
    ): Long =
        rollingPaperRepository.countNewerByParty(
            party = party,
            createdAt = rollingPaper.createdAt,
            rollingPaperId = rollingPaper.id,
        ) + 1
}
