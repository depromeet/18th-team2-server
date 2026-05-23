package com.team2.server.chat.infrastructure.sse

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ChatRealtimePartyEventBroadcasterTest {
    private val sseEmitterRegistry: SseEmitterRegistry = mock()
    private val broadcaster = ChatRealtimePartyEventBroadcaster(sseEmitterRegistry)
    private val now = LocalDateTime.of(2026, 5, 23, 14, 30)

    @Test
    fun `broadcasts realtime party events through chat SSE registry`() {
        broadcaster.broadcastHostEndAvailable(partyId = 1L, availableAt = now)
        broadcaster.broadcastPartyEnding(partyId = 1L, endingStartedAt = now, endedAt = now.plusSeconds(60))
        broadcaster.broadcastPartyEnded(partyId = 1L, endedAt = now.plusSeconds(60))
        broadcaster.completeParty(partyId = 1L)

        val eventCaptor = argumentCaptor<Set<ResponseBodyEmitter.DataWithMediaType>>()
        verify(sseEmitterRegistry).broadcastHost(eq(1L), any())
        verify(sseEmitterRegistry, times(2)).broadcast(eq(1L), eventCaptor.capture(), anyOrNull())
        verify(sseEmitterRegistry).completeAll(1L)
        assertEquals(listOf("party-ending", "party-ended"), eventCaptor.allValues.map(::eventName))
    }

    private fun eventName(event: Set<ResponseBodyEmitter.DataWithMediaType>): String {
        val rawEvent = event.joinToString(separator = "\n") { data -> data.data.toString() }
        return when {
            "event:party-ending" in rawEvent -> "party-ending"
            "event:party-ended" in rawEvent -> "party-ended"
            else -> error("Unexpected SSE event: $rawEvent")
        }
    }
}
