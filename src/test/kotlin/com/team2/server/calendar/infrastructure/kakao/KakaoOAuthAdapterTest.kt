package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.http.HttpMethod as SpringHttpMethod

class KakaoOAuthAdapterTest {
    private val builder = RestClient.builder().baseUrl("https://kauth.kakao.com")
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val adapter =
        KakaoOAuthAdapter(
            restClient = builder.build(),
            objectMapper = ObjectMapper(),
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
        )

    @Test
    fun `인가 코드를 토큰으로 교환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andExpect(content().string(containsString("code=auth-code")))
            .andExpect(content().string(containsString("client_id=test-client-id")))
            .andRespond(
                withSuccess(
                    """
                    {
                      "access_token": "access-1",
                      "expires_in": 21599,
                      "refresh_token": "refresh-1",
                      "refresh_token_expires_in": 5183999
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tokens = adapter.exchange("auth-code", "https://api.example.com/callback")

        assertEquals("access-1", tokens?.accessToken)
        assertEquals(21599L, tokens?.accessTokenExpiresInSeconds)
        assertEquals("refresh-1", tokens?.refreshToken)
        assertEquals(5183999L, tokens?.refreshTokenExpiresInSeconds)
        server.verify()
    }

    @Test
    fun `리프레시로 갱신한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andExpect(content().string(containsString("grant_type=refresh_token")))
            .andExpect(content().string(containsString("refresh_token=refresh-1")))
            .andRespond(
                withSuccess(
                    """{"access_token":"access-2","expires_in":21599}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tokens = adapter.refresh("refresh-1")

        assertEquals("access-2", tokens?.accessToken)
        assertNull(tokens?.refreshToken)
        assertNull(tokens?.refreshTokenExpiresInSeconds)
    }

    @Test
    fun `카카오가 거부하면 null 을 반환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"invalid_grant","error_description":"expired refresh token"}"""),
            )

        assertNull(adapter.refresh("refresh-1"))
    }

    @Test
    fun `카카오 장애면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `연결 실패도 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond { throw java.io.IOException("connect timed out") }

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `응답에 access_token 이 없으면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kauth.kakao.com/oauth/token"))
            .andRespond(withSuccess("""{"expires_in":21599}""", MediaType.APPLICATION_JSON))

        val exception = kotlin.runCatching { adapter.refresh("refresh-1") }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }
}
