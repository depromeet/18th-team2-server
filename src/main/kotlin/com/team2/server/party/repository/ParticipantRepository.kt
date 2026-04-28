package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByPartyAndUser(party: Party, user: User): Participant?
    fun existsByPartyAndUser(party: Party, user: User): Boolean
}
