package com.team2.server.config

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * p95/p99 지연시간 알림은 http_server_requests_seconds_bucket 시계열에 의존한다.
 * 이 버킷은 management.metrics.distribution.percentiles-histogram 으로만 생기는데,
 * 프로퍼티 이름이 틀리면 Spring 이 조용히 무시해 배포 후에야 알게 된다.
 * Boot 4 에서 해당 프로퍼티가 spring-boot-micrometer-metrics 모듈로 이동했으므로
 * 버전 업그레이드 시 이름이 다시 바뀔 수 있어 회귀 테스트로 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class MetricsHistogramTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val meterRegistry: PrometheusMeterRegistry,
    ) {
        @Test
        fun `HTTP 요청 지연시간이 히스토그램 버킷으로 발행된다`() {
            mockMvc.get("/api/me/account")

            assertThat(meterRegistry.scrape()).contains("http_server_requests_seconds_bucket")
        }

        @Test
        fun `SLO 경계값이 버킷으로 발행된다`() {
            mockMvc.get("/api/me/account")

            // 100ms, 300ms, 1s. "300ms 안에 처리된 비율" 을 히스토그램 보간 없이 계산할 수 있어야 한다.
            assertThat(bucketBoundaries()).contains(0.1, 0.3, 1.0)
        }

        @Test
        fun `버킷 범위가 설정한 상하한을 벗어나지 않는다`() {
            mockMvc.get("/api/me/account")

            // minimum/maximum-expected-value(5ms~10s)가 무시되면 버킷이 1ms~30s 전 구간으로
            // 퍼져 시계열이 불필요하게 늘어난다. +Inf 는 Prometheus 규격상 항상 포함된다.
            val boundaries = bucketBoundaries().filter { it.isFinite() }

            assertThat(boundaries.min()).isGreaterThanOrEqualTo(0.005)
            assertThat(boundaries.max()).isLessThanOrEqualTo(10.0)
        }

        private fun bucketBoundaries(): List<Double> =
            meterRegistry
                .scrape()
                .lineSequence()
                .filter { it.startsWith("http_server_requests_seconds_bucket") }
                .mapNotNull(::boundaryOf)
                .toList()

        private fun boundaryOf(line: String): Double? {
            val match = LE_PATTERN.find(line) ?: return null
            return match.groupValues[1].toDoubleOrNull()
        }

        private companion object {
            private val LE_PATTERN = """le="([^"]+)"""".toRegex()
        }
    }
