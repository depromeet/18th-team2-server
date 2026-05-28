package com.team2.server.common.image.application.port

import com.team2.server.common.image.entity.ImageTargetType

interface ImageUrlPort {
    fun findFirstImageUrlByTargetIds(
        targetType: ImageTargetType,
        targetIds: Collection<Long>,
    ): Map<Long, String>
}
