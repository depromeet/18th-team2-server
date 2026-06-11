package com.team2.server.party.application.usecase

import com.team2.server.party.api.dto.ArchiveListItemResponse
import com.team2.server.party.api.dto.ArchiveListResponse
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyArchiveUseCase(
    private val participantRepository: ParticipantRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(
        userId: Long?,
        cursor: Long?,
        size: Int,
    ): ArchiveListResponse {
        if (userId == null) return ArchiveListResponse.EMPTY

        val rows =
            participantRepository.findArchiveByUserId(
                userId = userId,
                cursor = cursor,
                pageable = PageRequest.of(0, size + 1),
            )
        val hasNext = rows.size > size
        val pageItems = rows.take(size)
        val items = pageItems.map { ArchiveListItemResponse.from(it, userId) }
        val nextCursor = if (hasNext) pageItems.last().id.toString() else null
        val totalCount = participantRepository.countArchiveByUserId(userId)

        return ArchiveListResponse(items = items, nextCursor = nextCursor, totalCount = totalCount)
    }
}
