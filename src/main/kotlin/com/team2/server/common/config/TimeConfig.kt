package com.team2.server.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of(SEOUL_ZONE_ID))

    companion object {
        const val SEOUL_ZONE_ID = "Asia/Seoul"
    }
}
