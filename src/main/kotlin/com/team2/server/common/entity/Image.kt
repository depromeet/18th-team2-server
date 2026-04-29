package com.team2.server.common.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "image",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_image_target_sort",
            columnNames = ["target_type", "target_id", "sort_order"],
        ),
    ],
)
class Image(
    @Column(name = "image_url", nullable = false)
    var imageUrl: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    var targetType: ImageTargetType,
    @Column(name = "target_id", nullable = false)
    var targetId: Long,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity()
