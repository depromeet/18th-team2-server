package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.UpcomingPartyResult
import com.team2.server.party.application.dto.UpcomingRealtimeScheduleResult
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetUpcomingPartiesUseCase(
    private val participantRepository: ParticipantRepository,
    private val partyInviteRepository: PartyInviteRepository,
) {
    @Transactional(readOnly = true)
    fun getUpcomingParties(userId: Long): List<UpcomingPartyResult> {
        val now = LocalDateTime.now()
        val participants =
            participantRepository.findNotEndedByUserId(
                userId = userId,
                startedAfter = now.minusDays(Party.ENDED_AFTER_DAYS),
                now = now,
            )
        val inviteTokenByPartyId = findInviteTokenByPartyId(participants.map { it.party }, now)

        return participants.map { participant ->
            val party = participant.party
            val isHost = party.ownerId == userId
            val realtimeParty = realtimePartyOrNull(party)
            UpcomingPartyResult(
                partyId = party.id,
                inviteToken = inviteTokenByPartyId[party.id],
                partyOption = party.partyOption,
                celebrantNickname = party.celebrantNickname,
                partyStartedAt = party.startedAt,
                partyEndedAt = party.endedAt(),
                isHost = isHost,
                rollingPaperWritten = participant.hasWrittenPaper,
                hostRollingPaperOpenAt = if (isHost) party.hostViewableAt() else null,
                realtimeSchedule = realtimeParty?.toRealtimeSchedule(),
                realtimeStatus = realtimeParty?.status(now),
                realtimeEnterable = realtimeParty?.isEnterable(now) ?: false,
            )
        }
    }

    private fun realtimePartyOrNull(party: Party): RealtimeParty? =
        if (party.partyOption == PartyOption.REALTIME) {
            Hibernate.unproxy(party) as RealtimeParty
        } else {
            null
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

    private fun RealtimeParty.toRealtimeSchedule(): UpcomingRealtimeScheduleResult =
        UpcomingRealtimeScheduleResult(
            enterableFrom = enterableFrom(),
            liveStartAt = startedAt,
            liveStartedAt = liveStartedAt,
            liveEndAt = effectiveEndingStartedAt(),
        )
}
