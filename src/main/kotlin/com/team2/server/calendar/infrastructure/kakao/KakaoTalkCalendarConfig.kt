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
class KakaoTalkCalendarConfig(
    @Value("\${kakao.talk-calendar.base-url:https://kapi.kakao.com}")
    private val baseUrl: String,
) {
    /**
     * UseCase 트랜잭션 안에서 호출되므로 타임아웃을 짧게 잡아 DB 커넥션 점유 시간을 제한한다.
     */
    @Bean
    fun kakaoTalkCalendarRestClient(): RestClient {
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
