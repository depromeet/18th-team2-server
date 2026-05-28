package com.team2.server.party.infrastructure.memory

import com.team2.server.party.domain.vo.PartyPhase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPartyPhaseStoreTest {
    private lateinit var store: InMemoryPartyPhaseStore
    private val now = LocalDateTime.of(2026, 5, 26, 20, 0)

    @BeforeEach
    fun setUp() {
        store = InMemoryPartyPhaseStore()
    }

    @Test
    fun `등록 전 getEntry는 null 반환`() {
        assertNull(store.getEntry(1L))
    }

    @Test
    fun `ENTRY에서 MUSIC으로 advance 성공`() {
        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        assertTrue(advanced)
        assertEquals(PartyPhase.MUSIC, store.getEntry(1L)?.phase)
        assertEquals(now, store.getEntry(1L)?.startedAt)
    }

    @Test
    fun `현재 phase가 from과 다르면 advance 실패`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now.plusSeconds(1))

        assertFalse(advanced)
        assertEquals(now, store.getEntry(1L)?.startedAt) // 변경 없음
    }

    @Test
    fun `null 상태(미등록)는 ENTRY로 간주`() {
        val advanced = store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)

        assertTrue(advanced)
    }

    @Test
    fun `removeByPartyId 후 getEntry는 null 반환`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)
        store.removeByPartyId(1L)

        assertNull(store.getEntry(1L))
    }

    @Test
    fun `서로 다른 partyId는 독립적으로 동작`() {
        store.advance(1L, PartyPhase.ENTRY, PartyPhase.MUSIC, now)
        store.advance(2L, PartyPhase.ENTRY, PartyPhase.MUSIC, now.plusMinutes(1))

        assertEquals(now, store.getEntry(1L)?.startedAt)
        assertEquals(now.plusMinutes(1), store.getEntry(2L)?.startedAt)
    }
}
