package com.team2.server.rollingpaper.repository

import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import org.springframework.data.jpa.repository.JpaRepository

interface RollingPaperWrapperRepository : JpaRepository<RollingPaperWrapper, Long> {
    fun findFirstByNameOrderByIdAsc(name: String): RollingPaperWrapper?
}
