package com.team2.server.common.image.persistence

import com.team2.server.common.image.application.port.ImageUrlPort
import com.team2.server.common.image.entity.ImageTargetType
import org.springframework.stereotype.Component

@Component
class ImageUrlReader(
    private val imageRepository: ImageRepository,
) : ImageUrlPort {
    override fun findFirstImageUrlByTargetIds(
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

    override fun findImageUrlByTargetIdsAndSortOrder(
        targetType: ImageTargetType,
        targetIds: Collection<Long>,
        sortOrder: Int,
    ): Map<Long, String> {
        if (targetIds.isEmpty()) {
            return emptyMap()
        }

        return imageRepository
            .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(targetType, targetIds.distinct())
            .filter { it.sortOrder == sortOrder }
            .associate { it.targetId to it.imageUrl }
    }
}
