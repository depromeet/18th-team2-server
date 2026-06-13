package com.team2.server.me.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "support")
data class SupportProperties(
    @field:NotBlank
    val chatUrl: String,
)
