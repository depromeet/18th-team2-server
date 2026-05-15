package com.team2.server.burstgame.application.service

import com.team2.server.burstgame.domain.BurstGameSnapshot

interface BurstGameEventBroadcaster {
    fun broadcastStarted(snapshot: BurstGameSnapshot)

    fun broadcastProgress(snapshot: BurstGameSnapshot)

    fun broadcastEnded(snapshot: BurstGameSnapshot)
}
