package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)

    fun findAllByPartyIdOrderByCreatedAtAsc(partyId: Long): List<ChatMessage>
}
