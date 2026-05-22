package com.team2.server.party.application.usecase

import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleBurstGameEndedUseCaseTest {
    private val partyRepository: PartyRepository = mock()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val useCase =
        HandleBurstGameEndedUseCase(
            partyRepository = partyRepository,
            clock = clock,
        )

    @Test
    fun `LIVE_OPEN realtime party returns true`() {
        val party = realtimeParty(id = 1L, startedAt = now.minusMinutes(1))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val result = useCase(1L)

        assertTrue(result)
    }

    @Test
    fun `non realtime party returns false`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = now)
        setId(party, 1L)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val result = useCase(1L)

        assertFalse(result)
    }

    @Test
    fun `LIVE_ENDING party returns false`() {
        val party =
            realtimeParty(
                id = 1L,
                startedAt = now.minusMinutes(5),
                liveEndingStartedAt = now.minusSeconds(1),
            )
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val result = useCase(1L)

        assertFalse(result)
    }

    @Test
    fun `missing party returns false`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val result = useCase(1L)

        assertFalse(result)
    }

    private fun realtimeParty(
        id: Long,
        startedAt: LocalDateTime,
        liveEndingStartedAt: LocalDateTime? = null,
    ): RealtimeParty =
        RealtimeParty(ownerId = 1L, startedAt = startedAt, liveEndingStartedAt = liveEndingStartedAt)
            .also { setId(it, id) }

    private fun setId(
        party: Party,
        id: Long,
    ) {
        var type: Class<*>? = party.javaClass
        while (type != null) {
            try {
                type.getDeclaredField("id").also { field ->
                    field.isAccessible = true
                    field.set(party, id)
                }
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            } catch (ex: ReflectiveOperationException) {
                throw IllegalStateException("Failed to set id=$id on party=${party.javaClass.name}", ex)
            }
        }
        throw IllegalStateException("Could not find id field to set id=$id on party=${party.javaClass.name}")
    }
}
