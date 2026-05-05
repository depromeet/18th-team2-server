package com.team2.server.rollingpaper.usecase

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.repository.ImageRepository
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
    private val imageRepository: ImageRepository,
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
        val totalCount = rollingPaperRepository.countByParty(party)
        val totalPages = calculateTotalPages(totalCount)
        if (totalCount == 0L || page > totalPages) {
            return RollingPaperPageResult(
                page = page,
                totalCount = totalCount,
                totalPages = totalPages,
                hasNext = false,
                items = emptyList(),
            )
        }

        val rollingPapers =
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
        val imageUrlByWrapperId = findImageUrlByWrapperId(rollingPapers)
        return RollingPaperPageResult(
            page = page,
            totalCount = totalCount,
            totalPages = totalPages,
            hasNext = page < totalPages,
            items =
                rollingPapers.map { rollingPaper ->
                    RollingPaperListItemResponse(
                        rollingPaperId = rollingPaper.id,
                        writerNickname = rollingPaper.writerNickname.orEmpty(),
                        wrapperImageUrl = imageUrlByWrapperId[rollingPaper.wrapper.id],
                    )
                },
        )
    }

    private fun calculateTotalPages(totalCount: Long): Int =
        if (totalCount == 0L) {
            0
        } else {
            ((totalCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
        }

    private fun findImageUrlByWrapperId(rollingPapers: List<RollingPaper>): Map<Long, String> {
        val wrapperIds = rollingPapers.map { it.wrapper.id }.distinct()
        if (wrapperIds.isEmpty()) {
            return emptyMap()
        }

        return imageRepository
            .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                wrapperIds,
            ).distinctBy { it.targetId }
            .associate { it.targetId to it.imageUrl }
    }

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
