package com.team2.server.calendar.infrastructure.kakao

import com.team2.server.calendar.application.port.TalkCalendarPort
import com.team2.server.calendar.domain.vo.CalendarEvent
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val CREATE_EVENT_PATH = "/v2/api/calendar/create/event"
private const val UPDATE_EVENT_PATH = "/v2/api/calendar/update/event/host"
private const val DEFAULT_CALENDAR_ID = "primary"
private const val RECUR_UPDATE_TYPE_ALL = "ALL"
private val KAKAO_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

@Component
class KakaoTalkCalendarAdapter(
    @Qualifier("kakaoTalkCalendarRestClient") private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value("\${app.time-zone:Asia/Seoul}") private val zoneId: ZoneId,
) : TalkCalendarPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun createEvent(
        accessToken: String,
        event: CalendarEvent,
    ): String {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("calendar_id", DEFAULT_CALENDAR_ID)
                add("event", eventJson(event))
            }
        val response = post(CREATE_EVENT_PATH, accessToken, body)
        if (!response.statusCode.is2xxSuccessful) {
            throw toBusinessException(response.statusCode)
        }
        return readEventId(response.body)
    }

    override fun updateEvent(
        accessToken: String,
        eventId: String,
        event: CalendarEvent,
    ): Boolean {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("calendar_id", DEFAULT_CALENDAR_ID)
                add("event_id", eventId)
                add("recur_update_type", RECUR_UPDATE_TYPE_ALL)
                add("event", eventJson(event))
            }
        val response = post(UPDATE_EVENT_PATH, accessToken, body)
        if (response.statusCode == HttpStatus.NOT_FOUND) {
            return false
        }
        if (!response.statusCode.is2xxSuccessful) {
            throw toBusinessException(response.statusCode)
        }
        return true
    }

    private fun post(
        path: String,
        accessToken: String,
        body: MultiValueMap<String, String>,
    ): ResponseEntity<String> =
        try {
            restClient
                .post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                // 상태 코드를 직접 분기하려고 기본 예외 변환을 끈다.
                .onStatus({ it.isError }) { _, _ -> }
                .toEntity(String::class.java)
        } catch (e: RestClientException) {
            log.warn("카카오 톡캘린더 호출 실패. path={}", path, e)
            throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun eventJson(event: CalendarEvent): String =
        objectMapper.writeValueAsString(
            mapOf(
                "title" to event.title,
                "time" to
                    mapOf(
                        "start_at" to event.startAt.toKakaoUtc(),
                        "end_at" to event.endAt.toKakaoUtc(),
                        "time_zone" to zoneId.id,
                        "all_day" to false,
                    ),
                "description" to event.description,
            ),
        )

    private fun readEventId(body: String?): String {
        val parsed =
            runCatching { objectMapper.readValue(body ?: "", Map::class.java) }
                .getOrElse { e ->
                    log.warn("카카오 응답 파싱 실패. bodyLength={}", body?.length ?: 0, e)
                    throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
                }
        return parsed["event_id"] as? String
            ?: throw BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
    }

    private fun toBusinessException(status: HttpStatusCode): BusinessException =
        when (status.value()) {
            HttpStatus.UNAUTHORIZED.value() -> BusinessException(ErrorCode.KAKAO_TOKEN_INVALID)
            HttpStatus.FORBIDDEN.value() -> BusinessException(ErrorCode.KAKAO_CALENDAR_CONSENT_REQUIRED)
            else -> BusinessException(ErrorCode.KAKAO_CALENDAR_UNAVAILABLE)
        }

    private fun LocalDateTime.toKakaoUtc(): String =
        atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).format(KAKAO_DATE_TIME)
}
