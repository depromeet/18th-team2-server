package com.team2.server.common.repository

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ImageRepository : JpaRepository<Image, Long> {
    fun countByTargetType(targetType: ImageTargetType): Long

    fun findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
        targetType: ImageTargetType,
        targetId: Long,
    ): Image?

    fun findByTargetTypeAndTargetIdAndSortOrder(
        targetType: ImageTargetType,
        targetId: Long,
        sortOrder: Int,
    ): Image?

    @Query(
        """
        select i
        from Image i
        where i.targetType = :targetType
          and i.targetId in :targetIds
        order by i.targetId asc, i.sortOrder asc
        """,
    )
    fun findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
        @Param("targetType") targetType: ImageTargetType,
        @Param("targetIds") targetIds: Collection<Long>,
    ): List<Image>
}
