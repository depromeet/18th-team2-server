package com.team2.server.rollingpaper.repository

import com.team2.server.party.entity.Party
import com.team2.server.rollingpaper.entity.RollingPaper
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface RollingPaperRepository : JpaRepository<RollingPaper, Long> {
    fun existsByPartyAndWriterNicknameKey(
        party: Party,
        writerNicknameKey: String,
    ): Boolean

    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
}
