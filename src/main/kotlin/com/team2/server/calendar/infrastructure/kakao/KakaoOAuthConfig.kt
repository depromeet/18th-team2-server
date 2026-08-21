package com.team2.server.calendar.infrastructure.kakao

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

private const val CONNECT_TIMEOUT_SECONDS = 2L
private const val READ_TIMEOUT_SECONDS = 5L

@Configuration
class KakaoOAuthConfig(
    @Value("\${kakao.auth.base-url:https://kauth.kakao.com}")
    private val baseUrl: String,
) {
    /** 토큰 확보 트랜잭션 안에서 호출되므로 일정 API 와 같은 타임아웃을 건다. */
    @Bean
    fun kakaoOAuthRestClient(): RestClient {
        val requestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
            }
        return RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
