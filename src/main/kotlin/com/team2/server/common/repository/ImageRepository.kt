package com.team2.server.common.repository

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import org.springframework.data.jpa.repository.JpaRepository

interface ImageRepository : JpaRepository<Image, Long> {
    fun findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
        targetType: ImageTargetType,
        targetId: Long,
    ): Image?

    fun findByTargetTypeAndTargetIdInOrderByTargetIdAscSortOrderAsc(
        targetType: ImageTargetType,
        targetIds: Collection<Long>,
    ): List<Image>
}
