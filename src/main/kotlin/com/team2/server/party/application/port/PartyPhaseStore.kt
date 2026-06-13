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

    fun advance(
        partyId: Long,
        from: PartyPhase,
        to: PartyPhase,
        now: LocalDateTime,
        beforeUpdate: () -> Unit,
    ): Boolean

    // 현재 페이즈에 관계없이 강제 설정 (파티 강제 종료 등)
    fun forceSet(
        partyId: Long,
        phase: PartyPhase,
        now: LocalDateTime,
    )

    fun removeByPartyId(partyId: Long)

    fun clear()

    data class PhaseEntry(
        val phase: PartyPhase,
        val startedAt: LocalDateTime,
    )
}
