package com.team2.server.auth.config

import com.team2.server.auth.jwt.JwtAuthenticationEntryPoint
import com.team2.server.auth.jwt.JwtAuthenticationFilter
import com.team2.server.auth.oauth2.CustomOAuth2UserService
import com.team2.server.auth.oauth2.OAuth2FailureHandler
import com.team2.server.auth.oauth2.OAuth2RedirectUriCaptureFilter
import com.team2.server.auth.oauth2.OAuth2SuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val oAuth2FailureHandler: OAuth2FailureHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val oAuth2Properties: OAuth2Properties,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/oauth2/**",
                        "/login/**",
                        "/actuator/health",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/api/dev/**",
                        "/api/v1/kakao-calendar/consent/**",
                    ).permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/characters").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/rolling-paper-toppings").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/party-invites/*").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/party-invites/*/rolling-papers").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/party-invites/*/rolling-papers").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/archive").permitAll()
                auth
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/party-invites/*/realtime-participants/stream",
                    ).permitAll()
                auth.requestMatchers(HttpMethod.DELETE, "/api/v1/parties/*/realtime-participants").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/chat-messages").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/candle-blow").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/candle-blow/candles/*").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/burst-game/taps").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/fireworks").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/burst-game").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/realtime-state").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/realtime-next-action").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/participants").permitAll()
                auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/phase").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/phase/advance").permitAll()
                auth.requestMatchers("/ws/**").permitAll()
                auth.anyRequest().authenticated()
            }.oauth2Login { oauth ->
                oauth.userInfoEndpoint { it.userService(customOAuth2UserService) }
                oauth.successHandler(oAuth2SuccessHandler)
                oauth.failureHandler(oAuth2FailureHandler)
            }.exceptionHandling { it.authenticationEntryPoint(jwtAuthenticationEntryPoint) }
            .addFilterBefore(
                OAuth2RedirectUriCaptureFilter(oAuth2Properties),
                OAuth2AuthorizationRequestRedirectFilter::class.java,
            ).addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins =
            oAuth2Properties.authorizedRedirectUris
                .mapNotNull { runCatching { java.net.URI(it) }.getOrNull() }
                .map { "${it.scheme}://${it.authority}" }
                .distinct()
        val config =
            CorsConfiguration().apply {
                allowedOrigins = origins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
