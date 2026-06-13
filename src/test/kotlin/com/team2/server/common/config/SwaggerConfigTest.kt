package com.team2.server.common.config

import com.jayway.jsonpath.JsonPath
import com.team2.server.config.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class SwaggerConfigTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @Test
        fun `선택 인증 API는 OpenAPI security에 bearer와 익명 인증을 함께 노출한다`() {
            val apiDocs = getApiDocs()

            assertOptionalAuthSecurity(
                apiDocs,
                "$.paths['/api/v1/party-invites/{inviteToken}'].get.security",
            )
            assertOptionalAuthSecurity(
                apiDocs,
                "$.paths['/api/v1/party-invites/{inviteToken}/rolling-papers'].post.security",
            )
            assertOptionalAuthSecurity(
                apiDocs,
                "$.paths['/api/v1/party-invites/{inviteToken}/rolling-papers'].get.security",
            )
        }

        @Test
        fun `실시간 파티 다음 행동 Swagger schema는 순환 참조 없이 독립된 oneOf schema를 노출한다`() {
            val apiDocs = getApiDocs()
            val result: Map<String, Any> =
                JsonPath.read(apiDocs, "$.components.schemas.RealtimePartyNextActionResult")
            val host: Map<String, Any> = JsonPath.read(apiDocs, "$.components.schemas.Host")
            val participant: Map<String, Any> = JsonPath.read(apiDocs, "$.components.schemas.Participant")

            assertEquals(
                listOf(
                    mapOf("\$ref" to "#/components/schemas/Host"),
                    mapOf("\$ref" to "#/components/schemas/Participant"),
                ),
                result["oneOf"],
            )
            assertFalse(host.containsKey("allOf"))
            assertFalse(participant.containsKey("allOf"))
            assertEquals(
                setOf("type", "partyId"),
                (host["properties"] as Map<*, *>).keys,
            )
            assertEquals(
                listOf("HOST_ROLLING_PAPER_LIST"),
                ((host["properties"] as Map<*, *>)["type"] as Map<*, *>)["enum"],
            )
            assertEquals(
                setOf("type", "inviteToken", "rollingPaperWritten"),
                (participant["properties"] as Map<*, *>).keys,
            )
            assertEquals(
                listOf("PARTICIPANT_ROLLING_PAPER_WRITE"),
                ((participant["properties"] as Map<*, *>)["type"] as Map<*, *>)["enum"],
            )
        }

        private fun getApiDocs(): String =
            mockMvc
                .get("/v3/api-docs")
                .andExpect {
                    status { isOk() }
                }.andReturn()
                .response
                .contentAsString

        private fun assertOptionalAuthSecurity(
            apiDocs: String,
            jsonPath: String,
        ) {
            val security: List<Map<String, List<String>>> = JsonPath.read(apiDocs, jsonPath)

            assertEquals(2, security.size)
            assertTrue(security[0].containsKey("Bearer Authentication"))
            assertTrue(security[0].getValue("Bearer Authentication").isEmpty())
            assertTrue(security[1].isEmpty())
        }
    }
