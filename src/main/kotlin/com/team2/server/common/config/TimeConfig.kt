package com.team2.server.common.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId
import java.util.TimeZone

@Configuration
class TimeConfig {
    @PostConstruct
    fun setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SEOUL_ZONE_ID))
    }

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of(SEOUL_ZONE_ID))

    companion object {
        const val SEOUL_ZONE_ID = "Asia/Seoul"
    }
}
