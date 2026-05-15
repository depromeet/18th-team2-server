package com.team2.server.chat.service

import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

@Component
class PartyEndScheduler(
    private val taskScheduler: TaskScheduler,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    private val scheduled = ConcurrentHashMap.newKeySet<Long>()

    fun scheduleIfNeeded(
        partyId: Long,
        startedAt: LocalDateTime,
    ) {
        if (!scheduled.add(partyId)) return

        val zone = ZoneId.systemDefault()
        val warnAt =
            startedAt
                .plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES - WARN_BEFORE_END_MINUTES)
                .atZone(zone)
                .toInstant()
        val closeAt =
            startedAt
                .plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
                .atZone(zone)
                .toInstant()

        taskScheduler.schedule({
            sseEmitterRegistry.broadcast(
                partyId,
                SseEmitter
                    .event()
                    .name("party-ending")
                    .data("{}")
                    .build(),
            )
        }, warnAt)

        taskScheduler.schedule({
            scheduled.remove(partyId)
            sseEmitterRegistry.broadcast(
                partyId,
                SseEmitter
                    .event()
                    .name("party-ended")
                    .data("{}")
                    .build(),
            )
            sseEmitterRegistry.completeAll(partyId)
        }, closeAt)
    }

    companion object {
        const val WARN_BEFORE_END_MINUTES: Long = 1
    }
}
