package com.team2.server.calendar.api

import com.team2.server.config.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class GuestTalkCalendarConsentControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `인증 없이도 비회원 동의 URL 을 발급한다`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "/invite/a796ba5f")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.consentUrl") { exists() }
                }
        }

        @Test
        fun `복귀 경로가 다른 호스트로 나가면 400`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "//evil.com")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }

        @Test
        fun `복귀 경로가 슬래시로 시작하지 않으면 400`() {
            mockMvc
                .get("/api/v1/party-invites/a796ba5f/talk-calendar/consent-url") {
                    param("returnPath", "evil.com")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_INPUT") }
                }
        }
    }
