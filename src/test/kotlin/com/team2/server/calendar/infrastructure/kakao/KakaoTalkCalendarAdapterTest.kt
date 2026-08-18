package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.http.HttpMethod as SpringHttpMethod

class KakaoTalkCalendarAdapterTest {
    private val builder = RestClient.builder().baseUrl("https://kapi.kakao.com")
    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
    private val adapter =
        KakaoTalkCalendarAdapter(
            restClient = builder.build(),
            objectMapper = ObjectMapper(),
            zoneId = ZoneId.of("Asia/Seoul"),
        )

    private val event =
        CalendarEvent(
            title = "지민님의 생일 파티",
            startAt = LocalDateTime.of(2026, 8, 20, 19, 0),
            endAt = LocalDateTime.of(2026, 8, 20, 19, 30),
            description = "초대 링크: https://example.com/invite/abc",
        )

    @Test
    fun `일정 생성 요청은 Bearer 토큰과 form 바디를 보내고 event_id 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(header("Authorization", "Bearer kakao-token"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("calendar_id=primary")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("20260820T100000Z")))
            .andRespond(withSuccess("""{"event_id":"event-1"}""", MediaType.APPLICATION_JSON))

        val eventId = adapter.createEvent("kakao-token", event)

        assertEquals("event-1", eventId)
        server.verify()
    }

    @Test
    fun `일정 수정 요청은 event_id 를 함께 보내고 true 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/update/event/host"))
            .andExpect(method(SpringHttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event_id=event-1")))
            .andRespond(withSuccess("""{"event_id":"event-1"}""", MediaType.APPLICATION_JSON))

        assertTrue(adapter.updateEvent("kakao-token", "event-1", event))
        server.verify()
    }

    @Test
    fun `수정 대상 일정이 없으면 false 를 반환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/update/event/host"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFalse(adapter.updateEvent("kakao-token", "event-1", event))
        server.verify()
    }

    @Test
    fun `401 이면 KAKAO_TOKEN_INVALID 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_TOKEN_INVALID, (exception as BusinessException).errorCode)
    }

    @Test
    fun `403 이면 KAKAO_CALENDAR_CONSENT_REQUIRED 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED, (exception as BusinessException).errorCode)
    }

    @Test
    fun `5xx 면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }

    @Test
    fun `생성 응답에 event_id 가 없으면 KAKAO_CALENDAR_UNAVAILABLE 로 변환한다`() {
        server
            .expect(requestTo("https://kapi.kakao.com/v2/api/calendar/create/event"))
            .andRespond(withSuccess("""{}""", MediaType.APPLICATION_JSON))

        val exception = kotlin.runCatching { adapter.createEvent("kakao-token", event) }.exceptionOrNull()

        assertEquals(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE, (exception as BusinessException).errorCode)
    }
}
