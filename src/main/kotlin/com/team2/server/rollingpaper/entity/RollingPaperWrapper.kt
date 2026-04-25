package com.team2.server.rollingpaper.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "rolling_paper_wrapper")
class RollingPaperWrapper(
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
