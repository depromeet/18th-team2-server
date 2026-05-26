package com.team2.server.rollingpaper.usecase

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.rollingpaper.dto.ArchiveMyPaperSectionResult
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetArchiveMyPaperSectionUseCase(
    private val rollingPaperRepository: RollingPaperRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    @Transactional(readOnly = true)
    fun invoke(
        party: Party,
        myParticipant: Participant,
    ): ArchiveMyPaperSectionResult {
        val paperCount = rollingPaperRepository.countByParty(party)
        val myPaper = rollingPaperRepository.findByWriter(myParticipant)
        val myPaperToppingImageUrl: String? =
            myPaper?.let {
                imageUrlReader
                    .findFirstImageUrlByTargetIds(
                        ImageTargetType.ROLLING_PAPER_WRAPPER,
                        listOf(it.topping.id),
                    )[it.topping.id]
            }
        return ArchiveMyPaperSectionResult(
            paperCount = paperCount,
            myPaperWritten = myPaper != null,
            myPaperContent = myPaper?.content,
            myPaperWriterNickname = myPaper?.writerNickname,
            myPaperToppingImageUrl = myPaperToppingImageUrl,
        )
    }
}
