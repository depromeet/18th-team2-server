package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.port.PartyPhaseStore
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class GetPartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseStore: PartyPhaseStore,
    private val profileService: RealtimeParticipantProfileService,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        phaseForLeftParticipant(party, participantToken, now)?.let { return it }

        participantService.validatePartyMember(party, userId, participantToken)
        val entry = phaseStore.getEntry(partyId)
        return PartyPhaseResult(
            partyId = partyId,
            phase = entry?.phase ?: PartyPhase.ENTRY,
            phaseStartedAt = entry?.startedAt ?: party.startedAt,
            serverNow = now,
        )
    }

    private fun phaseForLeftParticipant(
        party: RealtimeParty,
        participantToken: String?,
        now: LocalDateTime,
    ): PartyPhaseResult? {
        if (participantToken == null) return null
        val profile = profileService.findByParticipantToken(participantToken) ?: return null
        if (profile.participant.party.id != party.id || !profile.participant.hasLeft) return null
        return PartyPhaseResult(
            partyId = party.id,
            phase = PartyPhase.END,
            phaseStartedAt = party.liveEndingStartedAt ?: now,
            serverNow = now,
        )
    }
}
