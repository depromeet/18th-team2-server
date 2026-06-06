package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.application.dto.RealtimePartyEndingInfo
import com.team2.server.party.application.port.RealtimePartyEndingInfoPort
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class RealtimePartyEndingInfoAdapter(
    private val profileRepository: RealtimeParticipantProfileRepository,
) : RealtimePartyEndingInfoPort {
    override fun get(party: RealtimeParty): RealtimePartyEndingInfo = get(party, party.endingReason())

    override fun get(
        party: RealtimeParty,
        now: LocalDateTime,
    ): RealtimePartyEndingInfo = get(party, party.endingReason(now))

    private fun get(
        party: RealtimeParty,
        endingReason: RealtimePartyEndingReason?,
    ): RealtimePartyEndingInfo {
        val hostProfile =
            profileRepository.findByParticipantPartyIdAndParticipantIsCelebrantTrue(party.id)
                ?: throw IllegalStateException("Realtime party host profile not found. partyId=${party.id}")
        return RealtimePartyEndingInfo(
            endingReason = endingReason,
            hostNickname = hostProfile.nickname,
        )
    }
}
