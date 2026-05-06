package com.team2.server.common.service

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ImageQueryService(
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun findFirstImageUrlByTargetIds(
        targetType: ImageTargetType,
        targetIds: Collection<Long>,
    ): Map<Long, String> {
        if (targetIds.isEmpty()) {
            return emptyMap()
        }

        return imageRepository
            .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(targetType, targetIds.distinct())
            .distinctBy { it.targetId }
            .associate { it.targetId to it.imageUrl }
    }
}
