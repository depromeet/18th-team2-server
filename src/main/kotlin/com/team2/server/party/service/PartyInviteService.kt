package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.ActivateInviteLinkResponse
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
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

        if (!participantRepository.existsByPartyIdAndUserId(partyId, userId)) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }

        val now = LocalDateTime.now()

        val invite =
            partyInviteRepository.findByPartyIdAndExpiresAtAfter(partyId, now)
                ?: createInvite(party, now)

        return ActivateInviteLinkResponse(token = invite.token)
    }

    private fun createInvite(
        party: Party,
        now: LocalDateTime,
    ): PartyInvite {
        val expiresAt = party.endedAt ?: now.plusHours(EXPIRY_HOURS)
        return partyInviteRepository.save(
            PartyInvite(
                party = party,
                token = generateToken(),
                expiresAt = expiresAt,
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
        private const val EXPIRY_HOURS = 24L
    }
}
