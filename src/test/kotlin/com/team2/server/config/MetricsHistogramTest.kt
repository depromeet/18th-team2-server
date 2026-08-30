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
 * 잘못된 프로퍼티 이름을 Spring 이 조용히 무시하는 탓에, 버킷 수집이 꺼져도 배포 후에야
 * 드러난다. Boot 4 에서 이미 모듈이 이동한 이력이 있어 회귀 테스트로 고정한다.
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

            assertThat(bucketBoundaries()).contains(0.1, 0.3, 1.0)
        }

        @Test
        fun `버킷 범위가 설정한 상하한을 벗어나지 않는다`() {
            mockMvc.get("/api/me/account")

            // +Inf 는 Prometheus 규격상 항상 포함되므로 제외한다
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
