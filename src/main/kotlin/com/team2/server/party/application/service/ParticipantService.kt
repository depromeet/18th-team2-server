package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.exception.isConstraintViolation
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val userRepository: UserRepository,
) {
    fun joinMember(
        party: Party,
        user: User,
    ): Participant =
        participantRepository.findByPartyAndUser(party, user)
            ?: createMemberParticipant(party, user)

    fun joinAnonymousOrMember(
        party: Party,
        user: User?,
    ): Participant {
        if (user == null) return participantRepository.save(Participant(party = party))
        return joinMember(party, user)
    }

    fun findOrCreate(
        party: Party,
        userId: Long,
        user: User,
    ): Participant =
        participantRepository.findByPartyIdAndUserId(party.id, userId)
            ?: participantRepository.save(Participant(party = party, user = user))

    fun requireCallerParticipant(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): Participant =
        when {
            participantToken != null -> {
                resolveCallerByTokenOrNull(partyId, participantToken)
                    ?: userId?.let { resolveCallerByUser(partyId, it) }
                    ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            userId != null -> resolveCallerByUser(partyId, userId)
            else -> throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

    fun validatePartyMember(
        party: RealtimeParty,
        userId: Long?,
        participantToken: String?,
    ) {
        if (party.ownerId == userId) return
        requireCallerParticipant(
            partyId = party.id,
            userId = userId,
            participantToken = participantToken,
        )
    }

    fun leave(participant: Participant): Participant {
        participant.leave()
        return participant
    }

    fun resolveUser(userId: Long?): User? {
        if (userId == null) return null
        return userRepository.findByIdOrNull(userId)
            ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
    }

    private fun resolveCallerByUser(
        partyId: Long,
        userId: Long,
    ): Participant =
        participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

    private fun resolveCallerByTokenOrNull(
        partyId: Long,
        participantToken: String,
    ): Participant? {
        val profile =
            realtimeParticipantProfileRepository.findByParticipantToken(participantToken)
                ?: return null
        if (profile.participant.hasLeft) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
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
