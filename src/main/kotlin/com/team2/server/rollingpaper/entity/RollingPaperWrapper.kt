package com.team2.server.rollingpaper.entity

import com.team2.server.common.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "rolling_paper_wrapper",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_rolling_paper_wrapper_name",
            columnNames = ["name"],
        ),
    ],
)
class RollingPaperWrapper(
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
