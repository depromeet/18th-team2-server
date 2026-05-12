package com.team2.server.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        val requestId = UUID.randomUUID().toString()

        MDC.put("traceId", traceId)
        MDC.put("requestId", requestId)

        response.setHeader("X-Trace-Id", traceId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
