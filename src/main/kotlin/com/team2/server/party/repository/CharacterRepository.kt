package com.team2.server.party.repository

import com.team2.server.party.entity.Character
import org.springframework.data.jpa.repository.JpaRepository

interface CharacterRepository : JpaRepository<Character, Long>
