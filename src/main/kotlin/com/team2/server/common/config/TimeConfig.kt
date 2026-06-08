package com.team2.server.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of(TIME_ZONE_ID))

    companion object {
        private const val TIME_ZONE_ID = "Asia/Seoul"
    }
}
