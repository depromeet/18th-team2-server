package com.team2.server.party.application.port

import com.team2.server.party.domain.vo.PartyPhase
import java.time.LocalDateTime

interface PartyPhaseStore {
    fun getEntry(partyId: Long): PhaseEntry?

    // CAS: entries[partyId].phase == from 일 때만 to로 전환. 성공 시 true 반환.
    fun advance(
        partyId: Long,
        from: PartyPhase,
        to: PartyPhase,
        now: LocalDateTime,
    ): Boolean

    fun removeByPartyId(partyId: Long)

    fun clear()

    data class PhaseEntry(
        val phase: PartyPhase,
        val startedAt: LocalDateTime,
    )
}
