package com.team2.server.party.repository

import com.team2.server.party.entity.Party
import org.springframework.data.jpa.repository.JpaRepository

interface PartyRepository : JpaRepository<Party, Long> {
    fun findByShareLink(shareLink: String): Party?
}
