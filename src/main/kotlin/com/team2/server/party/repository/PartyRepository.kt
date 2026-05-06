package com.team2.server.party.repository

import com.team2.server.party.entity.Party
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PartyRepository : JpaRepository<Party, Long> {
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    fun findPartyById(id: Long): Party?
}
