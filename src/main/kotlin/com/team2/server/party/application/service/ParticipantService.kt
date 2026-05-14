package com.team2.server.party.application.service

import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.user.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
) {
    fun joinMember(
        party: Party,
        user: User,
    ): Participant =
        participantRepository.findByPartyAndUser(party, user)
            ?: createMemberParticipant(party, user)

    fun joinAnonymous(party: Party): Participant = participantRepository.save(Participant(party = party))

    fun findOrCreate(
        party: Party,
        userId: Long,
        user: User,
    ): Participant =
        participantRepository.findByPartyIdAndUserId(party.id, userId)
            ?: participantRepository.save(Participant(party = party, user = user))

    private fun createMemberParticipant(
        party: Party,
        user: User,
    ): Participant =
        try {
            participantRepository.saveAndFlush(
                Participant(
                    party = party,
                    user = user,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (!e.isConstraintViolation(PARTICIPANT_UNIQUE_CONSTRAINT)) {
                throw e
            }
            participantRepository.findByPartyAndUser(party, user) ?: throw e
        }

    companion object {
        private const val PARTICIPANT_UNIQUE_CONSTRAINT = "uk_participant_party_user"
    }
}
