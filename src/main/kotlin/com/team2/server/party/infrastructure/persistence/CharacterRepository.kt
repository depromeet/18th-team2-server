package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Character
import org.springframework.data.jpa.repository.JpaRepository

interface CharacterRepository : JpaRepository<Character, Long> {
    fun findByName(name: String): Character?
}
