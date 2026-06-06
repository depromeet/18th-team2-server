package com.team2.server.party.application.support

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

class RealtimePartyEndingInfoResolverTest {
    private val profileRepository: RealtimeParticipantProfileRepository = mock()
    private val resolver = RealtimePartyEndingInfoResolver(profileRepository)
    private val startedAt = LocalDateTime.of(2026, 6, 6, 10, 0)

    @Test
    fun `returns ending reason and host profile nickname`() {
        val party = realtimeParty(1L).apply { liveEndingStartedAt = startedAt.plusMinutes(5) }
        val profile = RealtimeParticipantProfile(Participant(party = party, isCelebrant = true), "주최자")
        whenever(profileRepository.findByParticipantPartyIdAndParticipantIsCelebrantTrue(1L)).thenReturn(profile)

        val result = resolver.get(party)

        assertEquals(RealtimePartyEndingReason.HOST_REQUEST, result.endingReason)
        assertEquals("주최자", result.hostNickname)
    }

    @Test
    fun `throws when host profile is missing`() {
        val party = realtimeParty(1L)
        whenever(profileRepository.findByParticipantPartyIdAndParticipantIsCelebrantTrue(1L)).thenReturn(null)

        assertThrows<IllegalStateException> { resolver.get(party) }
    }

    private fun realtimeParty(id: Long): RealtimeParty {
        val party = RealtimeParty(ownerId = 1L, startedAt = startedAt)
        var type: Class<*>? = party.javaClass
        while (type != null) {
            try {
                type.getDeclaredField("id").also { field ->
                    field.isAccessible = true
                    field.set(party, id)
                }
                return party
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Could not set party id")
    }
}
