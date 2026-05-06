package com.team2.server.rollingpaper.usecase

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.service.ImageQueryService
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListResponse
import com.team2.server.rollingpaper.dto.ParticipantRollingPaperListResponse
import com.team2.server.rollingpaper.dto.RollingPaperListItemResponse
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetRollingPaperListUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val partyRepository: PartyRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val imageQueryService: ImageQueryService,
) {
    @Transactional(readOnly = true)
    fun getParticipantList(
        inviteToken: String,
        page: Int,
    ): ParticipantRollingPaperListResponse {
        val party =
            partyInviteRepository.findByToken(inviteToken)?.party
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val pageResult = getPageResult(party, page)
        return ParticipantRollingPaperListResponse(
            partyOption = party.partyOption,
            liveEndAt =
                if (party.partyOption == PartyOption.REALTIME) {
                    party.hostViewableAt()
                } else {
                    null
                },
            page = pageResult.page,
            totalPages = pageResult.totalPages,
            hasNext = pageResult.hasNext,
            items = pageResult.items,
        )
    }

    @Transactional(readOnly = true)
    fun getOwnerList(
        partyId: Long,
        userId: Long,
        page: Int,
    ): OwnerRollingPaperListResponse {
        val party =
            partyRepository.findByIdOrNull(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        validateOwner(party, userId)

        val now = LocalDateTime.now()
        if (!party.canHostViewRollingPapers(now)) {
            throw BusinessException(ErrorCode.ROLLING_PAPER_NOT_VIEWABLE)
        }

        val pageResult = getPageResult(party, page)
        return OwnerRollingPaperListResponse(
            celebrantNickname = party.celebrantNickname,
            partyEndAt = party.endedAt(),
            page = pageResult.page,
            totalCount = pageResult.totalCount,
            totalPages = pageResult.totalPages,
            hasNext = pageResult.hasNext,
            items = pageResult.items,
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

    private fun getPageResult(
        party: Party,
        requestedPage: Int,
    ): RollingPaperPageResult {
        val page = requestedPage.coerceAtLeast(MIN_PAGE)
        val rollingPaperPage =
            rollingPaperRepository.findAllByParty(
                party,
                PageRequest.of(
                    page - 1,
                    PAGE_SIZE,
                    Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"),
                    ),
                ),
            )
        val rollingPapers = rollingPaperPage.content
        val imageUrlByWrapperId = findImageUrlByWrapperId(rollingPapers)
        return RollingPaperPageResult(
            page = page,
            totalCount = rollingPaperPage.totalElements,
            totalPages = rollingPaperPage.totalPages,
            hasNext = rollingPaperPage.hasNext(),
            items =
                rollingPapers.map { rollingPaper ->
                    RollingPaperListItemResponse(
                        rollingPaperId = rollingPaper.id,
                        writerNickname = rollingPaper.writerNickname,
                        wrapperImageUrl = imageUrlByWrapperId[rollingPaper.wrapper.id],
                    )
                },
        )
    }

    private fun findImageUrlByWrapperId(rollingPapers: List<RollingPaper>): Map<Long, String> =
        imageQueryService.findFirstImageUrlByTargetIds(
            ImageTargetType.ROLLING_PAPER_WRAPPER,
            rollingPapers.map { it.wrapper.id },
        )

    private data class RollingPaperPageResult(
        val page: Int,
        val totalCount: Long,
        val totalPages: Int,
        val hasNext: Boolean,
        val items: List<RollingPaperListItemResponse>,
    )

    companion object {
        const val PAGE_SIZE: Int = 7
        private const val MIN_PAGE: Int = 1
    }
}
