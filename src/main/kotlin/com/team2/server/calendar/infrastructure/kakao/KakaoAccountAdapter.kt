package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.KakaoAccountPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

private const val TOKEN_INFO_PATH = "/v1/user/access_token_info"

/**
 * 일정 API 와 같은 호스트(`kapi.kakao.com`)라 `RestClient` 빈을 재사용한다.
 */
@Component
class KakaoAccountAdapter(
    @Qualifier("kakaoTalkCalendarRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : KakaoAccountPort {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("ReturnCount")
    override fun fetchProviderId(accessToken: String): String? {
        val response =
            try {
                restClient
                    .get()
                    .uri(TOKEN_INFO_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                    .exchange { _, res ->
                        ResponseEntity.status(res.statusCode).body(res.bodyTo(String::class.java))
                    }
            } catch (e: RestClientException) {
                log.warn("카카오 토큰 정보 조회 실패", e)
                return null
            }
        if (!response.statusCode.is2xxSuccessful) {
            log.warn("카카오 토큰 정보 조회 거부. status={}", response.statusCode.value())
            return null
        }
        val parsed = runCatching { objectMapper.readValue(response.body ?: "", Map::class.java) }.getOrNull()
        return (parsed?.get("id") as? Number)?.toLong()?.toString()
    }
}
