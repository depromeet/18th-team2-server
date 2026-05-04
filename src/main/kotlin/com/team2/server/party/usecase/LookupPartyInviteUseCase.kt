package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.PartyInviteLookupResponse
import com.team2.server.party.dto.RealtimeStatus
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class LookupPartyInviteUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
) {
    @Transactional(readOnly = true)
    fun lookup(
        inviteToken: String,
        userId: Long?,
    ): PartyInviteLookupResponse {
        val party =
            partyInviteRepository.findByToken(inviteToken)?.party
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        val now = LocalDateTime.now()
        val partyEndAt = party.createdAt.plusDays(Party.ENDED_AFTER_DAYS)
        val isRealtime = party.partyOption == PartyOption.REALTIME

        return PartyInviteLookupResponse(
            celebrantNickname = party.celebrantNickname,
            partyOption = party.partyOption,
            partyEnded = !now.isBefore(partyEndAt),
            rollingPaperWritten = hasWrittenPaper(party, userId),
            partyStartDate = party.createdAt.toLocalDate(),
            partyEndDate = partyEndAt.toLocalDate(),
            realtimeStatus = if (isRealtime) calculateRealtimeStatus(party, now) else null,
            liveStartAt = if (isRealtime) party.startedAt else null,
            liveDurationMinutes = if (isRealtime) RealtimeParty.LIVE_DURATION_MINUTES else null,
        )
    }

    private fun hasWrittenPaper(
        party: Party,
        userId: Long?,
    ): Boolean {
        if (userId == null) {
            return false
        }
        return participantRepository.findByPartyIdAndUserId(party.id, userId)?.hasWrittenPaper ?: false
    }

    private fun calculateRealtimeStatus(
        party: Party,
        now: LocalDateTime,
    ): RealtimeStatus {
        val enterableAt = party.startedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
        val liveEndAt = party.startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)

        return when {
            now < enterableAt -> RealtimeStatus.NOT_ENTERABLE
            now < liveEndAt -> RealtimeStatus.ENTERABLE
            else -> RealtimeStatus.ENDED
        }
    }
}
