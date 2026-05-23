package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListItemResponse
import com.team2.server.rollingpaper.dto.OwnerRollingPaperListResponse
import com.team2.server.rollingpaper.dto.ParticipantRollingPaperListItemResponse
import com.team2.server.rollingpaper.dto.ParticipantRollingPaperListResponse
import com.team2.server.rollingpaper.dto.RollingPaperPageInfoResponse
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
    private val imageUrlReader: ImageUrlReader,
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
            items =
                pageResult.items.map { item ->
                    ParticipantRollingPaperListItemResponse(
                        rollingPaperId = item.rollingPaperId,
                        writerNickname = item.writerNickname,
                        toppingImageUrl = item.toppingImageUrl,
                    )
                },
            pageInfo = pageResult.toPageInfoResponse(),
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
            items =
                pageResult.items.map { item ->
                    OwnerRollingPaperListItemResponse(
                        rollingPaperId = item.rollingPaperId,
                        position = item.position,
                        writerNickname = item.writerNickname,
                        content = item.content,
                        toppingImageUrl = item.toppingImageUrl,
                    )
                },
            pageInfo = pageResult.toPageInfoResponse(),
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
        val imageUrlByToppingId = findImageUrlByToppingId(rollingPapers)
        return RollingPaperPageResult(
            page = page,
            totalCount = rollingPaperPage.totalElements,
            totalPages = rollingPaperPage.totalPages,
            hasNext = rollingPaperPage.hasNext(),
            items =
                rollingPapers.mapIndexed { index, rollingPaper ->
                    RollingPaperPageItem(
                        rollingPaperId = rollingPaper.id,
                        position = calculatePosition(page, index),
                        writerNickname = rollingPaper.writerNickname,
                        content = rollingPaper.content,
                        toppingImageUrl = requireToppingImageUrl(imageUrlByToppingId, rollingPaper.topping.id),
                    )
                },
        )
    }

    private fun calculatePosition(
        page: Int,
        index: Int,
    ): Long = ((page - 1) * PAGE_SIZE + index + 1).toLong()

    private fun findImageUrlByToppingId(rollingPapers: List<RollingPaper>): Map<Long, String> =
        imageUrlReader.findFirstImageUrlByTargetIds(
            ImageTargetType.ROLLING_PAPER_WRAPPER,
            rollingPapers.map { it.topping.id },
        )

    private fun requireToppingImageUrl(
        imageUrlByToppingId: Map<Long, String>,
        toppingId: Long,
    ): String =
        imageUrlByToppingId[toppingId]
            ?: throw BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Rolling paper topping image is missing: toppingId=$toppingId",
            )

    private data class RollingPaperPageResult(
        val page: Int,
        val totalCount: Long,
        val totalPages: Int,
        val hasNext: Boolean,
        val items: List<RollingPaperPageItem>,
    ) {
        fun toPageInfoResponse(): RollingPaperPageInfoResponse =
            RollingPaperPageInfoResponse(
                page = page,
                totalCount = totalCount,
                totalPages = totalPages,
                hasNext = hasNext,
            )
    }

    private data class RollingPaperPageItem(
        val rollingPaperId: Long,
        val position: Long,
        val writerNickname: String,
        val content: String,
        val toppingImageUrl: String,
    )

    companion object {
        const val PAGE_SIZE: Int = 7
        private const val MIN_PAGE: Int = 1
    }
}
