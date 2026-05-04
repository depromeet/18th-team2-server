package com.team2.server.rollingpaper.repository

import com.team2.server.party.entity.Party
import com.team2.server.rollingpaper.entity.RollingPaper
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RollingPaperRepository : JpaRepository<RollingPaper, Long> {
    fun existsByPartyAndWriterNickname(
        party: Party,
        writerNickname: String,
    ): Boolean

    @Query("select r.writerNickname from RollingPaper r where r.party.id = :partyId")
    fun findWriterNicknamesByPartyId(
        @Param("partyId") partyId: Long,
    ): List<String>
}
