package com.team2.server.burstgame.infrastructure.scheduler

import com.team2.server.burstgame.application.service.BurstGameSessionStore
import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class BurstGameSessionCleanupScheduler(
    private val sessionStore: BurstGameSessionStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    @PostConstruct
    fun start() {
        val delayMillis = BurstGamePolicy.ENDED_SESSION_TTL.toMillis()
        executor.scheduleWithFixedDelay({ cleanup() }, delayMillis, delayMillis, TimeUnit.MILLISECONDS)
    }

    fun cleanup(now: LocalDateTime = LocalDateTime.now()) {
        runCatching {
            sessionStore.removeExpired(now)
        }.onFailure { ex ->
            log.error("Failed to cleanup expired burst game sessions.", ex)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}
