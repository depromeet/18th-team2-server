package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.KakaoOAuthPort
import com.team2.server.calendar.application.port.KakaoOAuthTokens
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper

private const val TOKEN_PATH = "/oauth/token"
private const val ERROR_BODY_LOG_MAX_LENGTH = 500

@Component
class KakaoOAuthAdapter(
    @Qualifier("kakaoOAuthRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val clientId: String,
    private val clientSecret: String,
) : KakaoOAuthPort {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인이 쓰는 카카오 자격증명을 그대로 재사용한다. */
    @Autowired
    constructor(
        @Qualifier("kakaoOAuthRestClient") restClient: RestClient,
        objectMapper: ObjectMapper,
        clientRegistrationRepository: ClientRegistrationRepository,
    ) : this(
        restClient = restClient,
        objectMapper = objectMapper,
        clientId = clientRegistrationRepository.findByRegistrationId("kakao").clientId,
        clientSecret = clientRegistrationRepository.findByRegistrationId("kakao").clientSecret,
    )

    override fun exchange(
        code: String,
        redirectUri: String,
    ): KakaoOAuthTokens? {
        val form =
            baseForm("authorization_code").apply {
                add("redirect_uri", redirectUri)
                add("code", code)
            }
        return requestTokens(form)
    }

    override fun refresh(refreshToken: String): KakaoOAuthTokens? {
        val form =
            baseForm("refresh_token").apply {
                add("refresh_token", refreshToken)
            }
        return requestTokens(form)
    }

    private fun baseForm(grantType: String): MultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("grant_type", grantType)
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }

    private fun requestTokens(form: MultiValueMap<String, String>): KakaoOAuthTokens? {
        val response = post(form)
        if (response.statusCode.is4xxClientError) {
            log.warn(
                "카카오 토큰 요청 거부. status={}, body={}",
                response.statusCode.value(),
                response.body?.take(ERROR_BODY_LOG_MAX_LENGTH),
            )
            if (!isInvalidGrant(response.body)) {
                throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
            }
            return null
        }
        if (!response.statusCode.is2xxSuccessful) {
            log.warn(
                "카카오 토큰 요청 실패. status={}, body={}",
                response.statusCode.value(),
                response.body?.take(ERROR_BODY_LOG_MAX_LENGTH),
            )
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }
        return parse(response.body)
    }

    /**
     * 자격증명이 무효해졌을 때만 `null` 이다.
     *
     * 4xx 를 통째로 `null` 로 뭉뚱그리면 갱신 경로가 429 나 `invalid_client` 까지 "리프레시 토큰이 죽었다"로
     * 읽고 멀쩡한 연동을 지운다. 되살릴 수 있는 실패는 장애로 올려 연동을 남긴다.
     */
    private fun isInvalidGrant(body: String?): Boolean =
        runCatching { objectMapper.readValue(body ?: "", Map::class.java)["error"] == "invalid_grant" }
            .getOrDefault(false)

    private fun post(form: MultiValueMap<String, String>): ResponseEntity<String> =
        try {
            restClient
                .post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                // retrieve().onStatus 로 예외 변환만 끄면 오류 응답 본문이 소비돼 body 가 null 이 된다.
                // 카카오가 실패 사유를 본문에 담아 주므로 exchange 로 상태와 본문을 직접 읽는다.
                .exchange { _, response ->
                    ResponseEntity
                        .status(response.statusCode)
                        .body(response.bodyTo(String::class.java))
                }
        } catch (e: RestClientException) {
            log.warn("카카오 인증 서버 호출 실패", e)
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun parse(body: String?): KakaoOAuthTokens {
        val parsed = parseJson(body)
        return KakaoOAuthTokens(
            accessToken = requireAccessToken(parsed),
            accessTokenExpiresInSeconds = requireExpiresIn(parsed),
            refreshToken = parsed["refresh_token"] as? String,
            refreshTokenExpiresInSeconds = (parsed["refresh_token_expires_in"] as? Number)?.toLong(),
        )
    }

    private fun parseJson(body: String?): Map<*, *> =
        runCatching { objectMapper.readValue(body ?: "", Map::class.java) }
            .getOrElse { e ->
                log.warn("카카오 토큰 응답 파싱 실패. bodyLength={}", body?.length ?: 0, e)
                throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
            }

    private fun requireAccessToken(parsed: Map<*, *>): String =
        parsed["access_token"] as? String
            ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)

    private fun requireExpiresIn(parsed: Map<*, *>): Long =
        (parsed["expires_in"] as? Number)?.toLong()
            ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
}
