package com.team2.server.rollingpaper.repository

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.rollingpaper.entity.RollingPaper
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface RollingPaperRepository : JpaRepository<RollingPaper, Long> {
    fun existsByPartyAndWriterNicknameKey(
        party: Party,
        writerNicknameKey: String,
    ): Boolean

    @EntityGraph(attributePaths = ["wrapper"])
    fun findAllByParty(
        party: Party,
        pageable: Pageable,
    ): Page<RollingPaper>

    fun findByIdAndParty(
        id: Long,
        party: Party,
    ): RollingPaper?

    fun countByParty(party: Party): Long

    fun findByWriter(writer: Participant): RollingPaper?

    @Query(
        """
            SELECT COUNT(rp)
            FROM RollingPaper rp
            WHERE rp.party = :party
              AND (
                rp.createdAt > :createdAt OR
                (rp.createdAt = :createdAt AND rp.id > :rollingPaperId)
              )
        """,
    )
    fun countNewerByParty(
        @Param("party") party: Party,
        @Param("createdAt") createdAt: LocalDateTime,
        @Param("rollingPaperId") rollingPaperId: Long,
    ): Long

    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
}
