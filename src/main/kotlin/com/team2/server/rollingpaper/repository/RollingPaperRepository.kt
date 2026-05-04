package com.team2.server.rollingpaper.repository

import com.team2.server.party.entity.Party
import com.team2.server.rollingpaper.entity.RollingPaper
import org.springframework.data.jpa.repository.JpaRepository

interface RollingPaperRepository : JpaRepository<RollingPaper, Long> {
    fun existsByPartyAndWriterNickname(
        party: Party,
        writerNickname: String,
    ): Boolean
}
