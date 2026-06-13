package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.domain.candle.CandleBlowPolicy
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class CandleBlowSessionCleanupScheduler(
    private val sessionStore: CandleBlowSessionStore,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "candle-blow-session-cleanup")
        }

    @PostConstruct
    fun start() {
        val delayMillis = CandleBlowPolicy.SESSION_TTL.toMillis()
        executor.scheduleWithFixedDelay({ cleanup() }, delayMillis, delayMillis, TimeUnit.MILLISECONDS)
    }

    fun cleanup(now: LocalDateTime = LocalDateTime.now(clock)) {
        runCatching {
            sessionStore.removeExpired(now)
        }.onFailure { ex ->
            log.error("Failed to cleanup expired candle blow sessions.", ex)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}
