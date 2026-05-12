package com.team2.server.party.application.usecase

import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.dto.UpcomingPartyResponse
import com.team2.server.party.dto.UpcomingRealtimeScheduleResponse
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetUpcomingPartiesUseCase(
    private val participantRepository: ParticipantRepository,
    private val partyInviteRepository: PartyInviteRepository,
) {
    @Transactional(readOnly = true)
    fun getUpcomingParties(userId: Long): List<UpcomingPartyResponse> {
        val now = LocalDateTime.now()
        val participants =
            participantRepository.findNotEndedByUserId(
                userId = userId,
                startedAfter = now.minusDays(Party.ENDED_AFTER_DAYS),
            )
        val inviteTokenByPartyId = findInviteTokenByPartyId(participants.map { it.party }, now)

        return participants.map { participant ->
            val party = participant.party
            val isHost = party.ownerId == userId
            UpcomingPartyResponse(
                partyId = party.id,
                inviteToken = inviteTokenByPartyId[party.id],
                partyOption = party.partyOption,
                celebrantNickname = party.celebrantNickname,
                partyStartedAt = party.startedAt,
                partyEndedAt = party.endedAt(),
                isHost = isHost,
                rollingPaperWritten = participant.hasWrittenPaper,
                hostRollingPaperOpenAt = if (isHost) party.hostViewableAt() else null,
                realtimeSchedule =
                    if (party.partyOption == PartyOption.REALTIME) {
                        (party as RealtimeParty).toRealtimeSchedule()
                    } else {
                        null
                    },
            )
        }
    }

    private fun findInviteTokenByPartyId(
        parties: List<Party>,
        now: LocalDateTime,
    ): Map<Long, String> {
        val partyIds = parties.map { it.id }.distinct()
        if (partyIds.isEmpty()) {
            return emptyMap()
        }

        val tokenByPartyId = mutableMapOf<Long, String>()
        partyInviteRepository
            .findAllByPartyIdInAndExpiresAtAfter(partyIds, now)
            .forEach { invite -> tokenByPartyId.putIfAbsent(invite.party.id, invite.token) }
        return tokenByPartyId
    }

    private fun RealtimeParty.toRealtimeSchedule(): UpcomingRealtimeScheduleResponse =
        UpcomingRealtimeScheduleResponse(
            enterableFrom = startedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES),
            liveStartAt = startedAt,
            liveEndAt = startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES),
        )
}
