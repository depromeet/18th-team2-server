package com.team2.server.rollingpaper.repository

import com.team2.server.rollingpaper.entity.RollingPaperTopping
import org.springframework.data.jpa.repository.JpaRepository

interface RollingPaperToppingRepository : JpaRepository<RollingPaperTopping, Long> {
    fun findByName(name: String): RollingPaperTopping?
}
