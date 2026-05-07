package com.team2.server.auth.config

import jakarta.validation.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.oauth2")
data class OAuth2Properties(
    @field:NotEmpty
    val authorizedRedirectUris: List<String>,
    val cookieSecure: Boolean = false,
)
