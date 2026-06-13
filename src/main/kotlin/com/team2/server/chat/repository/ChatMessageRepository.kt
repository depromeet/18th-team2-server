package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)

    fun findAllByPartyIdOrderByCreatedAtAsc(partyId: Long): List<ChatMessage>

    @Query(
        """
        SELECT m FROM ChatMessage m
        JOIN FETCH m.profile p
        LEFT JOIN FETCH p.character
        WHERE m.party.id = :partyId
        ORDER BY m.createdAt ASC
        """,
    )
    fun findAllByPartyIdWithProfileOrderByCreatedAtAsc(
        @Param("partyId") partyId: Long,
    ): List<ChatMessage>

    fun countByPartyId(partyId: Long): Long

    @Query(
        "SELECT m FROM ChatMessage m " +
            "JOIN FETCH m.profile p " +
            "WHERE m.party.id = :partyId " +
            "ORDER BY m.createdAt DESC, m.id DESC",
    )
    fun findRecentByPartyId(
        @Param("partyId") partyId: Long,
        pageable: Pageable,
    ): List<ChatMessage>
}
