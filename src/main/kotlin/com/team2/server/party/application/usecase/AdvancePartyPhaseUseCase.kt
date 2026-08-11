package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.PartyPhaseResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyPhaseTransitionService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.vo.PartyPhase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class AdvancePartyPhaseUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val phaseTransitionService: PartyPhaseTransitionService,
    private val markRealtimePartyStartedUseCase: MarkRealtimePartyStartedUseCase,
    private val clock: Clock,
) {
    @Transactional
    operator fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        currentPhase: PartyPhase,
    ): PartyPhaseResult {
        val now = LocalDateTime.now(clock)
        val party = partyService.requireRealtimeParty(partyId)
        val nextPhase =
            ALLOWED_TRANSITIONS[currentPhase]
                ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        validateActor(party, currentPhase, userId, participantToken)

        val advanced = phaseTransitionService.advance(partyId, currentPhase, nextPhase, now, userId, participantToken)
        if (!advanced) return unchangedResult(partyId, party, now)

        if (currentPhase == PartyPhase.ENTRY) {
            markRealtimePartyStartedUseCase(partyId, now)
        }

        return PartyPhaseResult(
            partyId = partyId,
            phase = nextPhase,
            phaseStartedAt = now,
            serverNow = now,
        )
    }

    private fun validateActor(
        party: RealtimeParty,
        currentPhase: PartyPhase,
        userId: Long?,
        participantToken: String?,
    ) {
        when (currentPhase) {
            PartyPhase.ENTRY -> if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            PartyPhase.MUSIC,
            PartyPhase.CANDLE,
            -> participantService.validatePartyMember(party, userId, participantToken)
            else -> Unit
        }
    }

    private fun unchangedResult(
        partyId: Long,
        party: RealtimeParty,
        now: LocalDateTime,
    ): PartyPhaseResult {
        val entry = phaseTransitionService.getEntry(partyId)
        return PartyPhaseResult(
            partyId = partyId,
            phase = entry?.phase ?: PartyPhase.ENTRY,
            phaseStartedAt = entry?.startedAt ?: party.startedAt,
            serverNow = now,
        )
    }

    companion object {
        val ALLOWED_TRANSITIONS =
            mapOf(
                PartyPhase.ENTRY to PartyPhase.MUSIC,
                PartyPhase.MUSIC to PartyPhase.CANDLE,
                PartyPhase.CANDLE to PartyPhase.BURST,
            )
    }
}
