package com.team2.server.auth.config

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    @field:NotBlank
    val secret: String,
    val expirationHours: Long = 24,
)
