package com.team2.server.chat.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ChatSchedulerConfig {
    @Bean
    fun chatTaskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = POOL_SIZE
            setThreadNamePrefix("chat-scheduler-")
            initialize()
        }

    companion object {
        private const val POOL_SIZE = 2
    }
}
