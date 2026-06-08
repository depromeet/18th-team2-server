package com.team2.server.common.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class HttpExchangeLoggingFilterTest {
    private val filter = HttpExchangeLoggingFilter()

    @Test
    fun `json 요청과 응답을 로깅하고 응답 body를 유지한다`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(HttpExchangeLoggingFilter::class.java) as Logger
        val originalLevel = logger.level
        logger.level = Level.INFO
        logger.addAppender(appender)

        try {
            val request = jsonRequest()
            val response =
                MockHttpServletResponse().apply {
                    characterEncoding = Charsets.UTF_8.name()
                }
            val chain =
                FilterChain { servletRequest, servletResponse ->
                    servletRequest.inputStream.readAllBytes()
                    servletResponse.contentType = MediaType.APPLICATION_JSON_VALUE
                    servletResponse.writer.write("""{"token":"server-token","ok":true}""")
                }

            filter.doFilter(request, response, chain)

            assertEquals("""{"token":"server-token","ok":true}""", response.contentAsString)
            val logMessage = appender.list.single().formattedMessage
            assertContains(logMessage, "uri=/api/v1/parties/1/participants?token=***&page=1")
            assertContains(logMessage, """requestJson={"participantToken":"***","nickname":"neo"}""")
            assertContains(logMessage, """responseJson={"token":"***","ok":true}""")
        } finally {
            logger.detachAppender(appender)
            logger.level = originalLevel
        }
    }

    @Test
    fun `stream 요청은 응답을 캐싱하지 않고 요청 body를 로깅한다`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(HttpExchangeLoggingFilter::class.java) as Logger
        val originalLevel = logger.level
        logger.level = Level.INFO
        logger.addAppender(appender)

        try {
            val request =
                MockHttpServletRequest("POST", "/api/v1/party-invites/invite-code/realtime-participants/stream").apply {
                    contentType = MediaType.APPLICATION_JSON_VALUE
                    characterEncoding = Charsets.UTF_8.name()
                    setContent("""{"nickname":"토끼왕","characterId":1}""".toByteArray())
                }
            val response =
                MockHttpServletResponse().apply {
                    characterEncoding = Charsets.UTF_8.name()
                }
            val chain =
                FilterChain { servletRequest, servletResponse ->
                    servletRequest.inputStream.readAllBytes()
                    servletResponse.contentType = MediaType.APPLICATION_JSON_VALUE
                    servletResponse.writer.write("""{"status":400,"error":{"code":"CHAT_NOT_ACTIVE"}}""")
                }

            filter.doFilter(request, response, chain)

            assertEquals("""{"status":400,"error":{"code":"CHAT_NOT_ACTIVE"}}""", response.contentAsString)
            val logMessage = appender.list.single().formattedMessage
            assertContains(logMessage, "HTTP stream exchange")
            assertContains(logMessage, "uri=/api/v1/party-invites/invite-code/realtime-participants/stream")
            assertContains(logMessage, """requestJson={"nickname":"토끼왕","characterId":1}""")
        } finally {
            logger.detachAppender(appender)
            logger.level = originalLevel
        }
    }

    private fun jsonRequest(): MockHttpServletRequest =
        MockHttpServletRequest("POST", "/api/v1/parties/1/participants").apply {
            contentType = MediaType.APPLICATION_JSON_VALUE
            characterEncoding = Charsets.UTF_8.name()
            queryString = "token=secret-token&page=1"
            setContent("""{"participantToken":"secret-token","nickname":"neo"}""".toByteArray())
        }
}
