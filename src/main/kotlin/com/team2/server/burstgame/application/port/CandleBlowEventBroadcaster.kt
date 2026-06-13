package com.team2.server.burstgame.application.port

import com.team2.server.burstgame.domain.candle.CandleBlowSnapshot

interface CandleBlowEventBroadcaster {
    fun broadcastStarted(snapshot: CandleBlowSnapshot)

    fun broadcastProgress(snapshot: CandleBlowSnapshot)

    fun broadcastEnded(snapshot: CandleBlowSnapshot)
}
