package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class PartyInviteService(
    private val partyRepository: PartyRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
) {
    fun activateInviteLink(
        partyId: Long,
        userId: Long,
    ): String {
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

        return invite.token
    }

    fun resolveEnterableRealtimeInvite(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyInvite {
        val invite = requireValidInvite(inviteToken, now)
        requireRealtimeEnterWindow(invite.party, now)
        return invite
    }

    private fun requireValidInvite(
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

    private fun requireRealtimeEnterWindow(
        party: Party,
        now: LocalDateTime,
    ) {
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        val realtimeParty = Hibernate.unproxy(party) as RealtimeParty
        val enterableFrom = realtimeParty.startedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
        val enterableTo = realtimeParty.startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
        if (now.isBefore(enterableFrom) || !now.isBefore(enterableTo)) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }
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

    fun findLatestUsableInviteToken(
        partyId: Long,
        now: LocalDateTime,
    ): String =
        partyInviteRepository
            .findFirstByPartyIdAndExpiresAtAfterOrderByCreatedAtDescIdDesc(partyId, now)
            ?.token ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    fun findUsableRealtimeInvite(
        inviteToken: String,
        now: LocalDateTime,
    ): PartyInvite {
        val invite = findUsableInvite(inviteToken, now)
        val party = invite.party
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
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
