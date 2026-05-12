package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.ActivateInviteLinkResponse
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class PartyInviteService(
    private val partyRepository: PartyRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
) {
    @Transactional
    fun activateInviteLink(
        partyId: Long,
        userId: Long,
    ): ActivateInviteLinkResponse {
        val party: Party =
            partyRepository.findByIdOrNull(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (!canActivateInviteLink(party, userId)) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }

        val now = LocalDateTime.now()

        val invite =
            partyInviteRepository.findByPartyIdAndExpiresAtAfter(partyId, now)
                ?: createInvite(party, now)

        return ActivateInviteLinkResponse(token = invite.token)
    }

    fun findUsableInvite(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyInvite {
        val invite =
            partyInviteRepository.findByToken(inviteToken)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (!invite.expiresAt.isAfter(now)) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }

        return invite
    }

    private fun canActivateInviteLink(
        party: Party,
        userId: Long,
    ): Boolean =
        party.ownerId == userId ||
            participantRepository.existsByPartyIdAndUserId(party.id, userId)

    private fun createInvite(
        party: Party,
        now: LocalDateTime,
    ): PartyInvite {
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        return partyInviteRepository.save(
            PartyInvite(
                party = party,
                token = generateToken(),
                expiresAt = party.endedAt(),
            ),
        )
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_BYTES = 8
    }
}
