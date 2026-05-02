package com.team2.server.party.repository

import com.team2.server.party.entity.Guest
import org.springframework.data.jpa.repository.JpaRepository

interface GuestRepository : JpaRepository<Guest, Long> {
    fun findByTokenHash(tokenHash: String): Guest?
}
