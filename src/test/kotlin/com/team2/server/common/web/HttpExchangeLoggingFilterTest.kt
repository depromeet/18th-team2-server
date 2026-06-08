package com.team2.server.common.web

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
        logger.addAppender(appender)

        try {
            val request =
                MockHttpServletRequest("POST", "/api/v1/parties/1/participants").apply {
                    contentType = MediaType.APPLICATION_JSON_VALUE
                    characterEncoding = Charsets.UTF_8.name()
                    setContent("""{"participantToken":"secret-token","nickname":"neo"}""".toByteArray())
                }
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
            assertContains(logMessage, """requestJson={"participantToken":"***","nickname":"neo"}""")
            assertContains(logMessage, """responseJson={"token":"***","ok":true}""")
        } finally {
            logger.detachAppender(appender)
        }
    }
}
