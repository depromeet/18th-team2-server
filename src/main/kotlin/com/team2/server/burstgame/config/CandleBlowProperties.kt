package com.team2.server.burstgame.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "candle-blow")
data class CandleBlowProperties(
    val durationSeconds: Long = 300L,
) {
    init {
        require(durationSeconds > 0) { "candle-blow.duration-seconds must be positive." }
    }
}
