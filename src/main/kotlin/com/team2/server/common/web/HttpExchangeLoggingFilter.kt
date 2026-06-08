package com.team2.server.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private const val MAX_LOG_BODY_LENGTH = 8_192
private const val SENSITIVE_JSON_FIELDS =
    "password|token|participantToken|accessToken|refreshToken|authorization|clientSecret"
private val SENSITIVE_JSON_FIELD_PATTERN =
    Regex(
        pattern = """(?i)("(?:$SENSITIVE_JSON_FIELDS)"\s*:\s*")[^"]*(")""",
    )

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class HttpExchangeLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI

        return uri.startsWith("/actuator") ||
            uri.contains("/stream") ||
            request.contentType?.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE) == true
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cachedRequest = ContentCachingRequestWrapper(request, MAX_LOG_BODY_LENGTH)
        val cachedResponse = ContentCachingResponseWrapper(response)
        val startedAt = System.currentTimeMillis()

        try {
            filterChain.doFilter(cachedRequest, cachedResponse)
        } finally {
            logHttpExchange(cachedRequest, cachedResponse, System.currentTimeMillis() - startedAt)
            cachedResponse.copyBodyToResponse()
        }
    }

    private fun logHttpExchange(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        elapsedMillis: Long,
    ) {
        if (!log.isInfoEnabled) {
            return
        }

        log.info(
            "HTTP exchange method={} uri={} status={} elapsedMs={} requestJson={} responseJson={}",
            request.method,
            requestUri(request),
            response.status,
            elapsedMillis,
            visibleBodyOrEmpty(
                body = request.contentAsByteArray,
                contentType = request.contentType,
                encoding = request.characterEncoding,
            ),
            visibleBodyOrEmpty(
                body = response.contentAsByteArray,
                contentType = response.contentType,
                encoding = response.characterEncoding,
            ),
        )
    }

    private fun requestUri(request: HttpServletRequest): String =
        request.requestURI + request.queryString?.let { "?$it" }.orEmpty()

    private fun visibleBodyOrEmpty(
        body: ByteArray,
        contentType: String?,
        encoding: String?,
    ): String {
        if (body.isEmpty() || !isJson(contentType)) {
            return ""
        }

        val charset = encoding?.let(Charset::forName) ?: StandardCharsets.UTF_8
        val rawBody = String(body, charset)
        val truncatedBody = rawBody.take(MAX_LOG_BODY_LENGTH)
        val maskedBody = maskSensitiveJsonFields(truncatedBody)

        return if (rawBody.length > MAX_LOG_BODY_LENGTH) {
            "$maskedBody...(truncated)"
        } else {
            maskedBody
        }
    }

    private fun isJson(contentType: String?): Boolean =
        contentType
            ?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
            ?.let { mediaType ->
                mediaType.includes(MediaType.APPLICATION_JSON) ||
                    mediaType.subtype.endsWith("+json", ignoreCase = true)
            }
            ?: false

    private fun maskSensitiveJsonFields(body: String): String = SENSITIVE_JSON_FIELD_PATTERN.replace(body, "$1***$2")
}
