package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
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

    fun requireCallerParticipantId(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): Long =
        requireCallerParticipant(
            partyId = partyId,
            userId = userId,
            participantToken = participantToken,
        ).id

    fun requireCallerParticipant(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): Participant =
        when {
            userId != null -> resolveCallerByUser(partyId, userId)
            participantToken != null -> resolveCallerByToken(partyId, participantToken)
            else -> throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

    private fun resolveCallerByUser(
        partyId: Long,
        userId: Long,
    ): Participant =
        participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

    private fun resolveCallerByToken(
        partyId: Long,
        participantToken: String,
    ): Participant {
        val profile =
            realtimeParticipantProfileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        if (profile.participant.party.id != partyId) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
        return profile.participant
    }

    fun findOrderedProfiles(partyId: Long): List<RealtimeParticipantProfile> =
        realtimeParticipantProfileRepository.findAllByPartyIdOrderByParticipantIdAsc(partyId)

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
