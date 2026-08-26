package com.team2.server.chat.controller

import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.io.BufferedReader
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SSE 스트림이 열려 있는 동안 DB 커넥션이 점유되지 않는지 검증한다.
 *
 * 2026-08-24 장애의 회귀 테스트다. `spring.jpa.open-in-view`(Spring Boot 기본값 true)가 켜져 있으면
 * 영속성 컨텍스트와 그에 딸린 JDBC 커넥션이 "요청이 끝날 때까지" 스레드에 묶인다. 그런데 이 엔드포인트는
 * SseEmitter 를 반환하므로 "요청이 끝날 때"가 스트림이 닫히는 시점(최대 약 46분)이다. 그 결과
 * 참가자 1명당 커넥션 1개가 영구 점유되고, 풀 기본값이 10이므로 동시 참가자 상한이 서비스 전체에서
 * 약 10명으로 묶였다. 실제로 8명 접속 시 추가 입장자의 요청이 커넥션을 기다리며 응답 없이 매달렸다.
 *
 * 검증하는 것은 "몇 명까지 버티는가"가 아니라 **커넥션이 스트림 수에 비례해 늘어나는가**다.
 * 상한을 확인하는 방식이면 누군가 풀 크기만 키워도 통과해 버려 같은 결함을 다시 놓친다.
 *
 * 여는 스트림 수를 풀 크기(기본 10)보다 작게 잡는 이유는 의도적이다. 풀을 고갈시키면 테스트가
 * 단언 실패가 아니라 커넥션 대기로 멈춰(30초 타임아웃) 원인이 드러나지 않는다. 회귀가 발생하면
 * 이 테스트는 매달리지 않고 실제 수치를 담은 메시지와 함께 실패해야 한다.
 *
 * `webEnvironment = RANDOM_PORT` 는 불가피하다. 실제 소켓이 열려 있어야 요청이 "아직 끝나지 않은"
 * 상태가 되는데, MockMvc 는 서블릿 컨테이너를 띄우지 않아 이 상태를 만들 수 없다.
 * docs/testing-rules.md 의 기존 fingerprint(ChatSocketControllerTest 와 동일 조합)에 맞춰
 * 새 컨텍스트가 생기지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class RealtimePartySseConnectionPoolTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var partyInviteRepository: PartyInviteRepository

    @Autowired
    private lateinit var characterRepository: CharacterRepository

    @Autowired
    private lateinit var participantRepository: ParticipantRepository

    @Autowired
    private lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository

    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build()

    private val openStreams = CopyOnWriteArrayList<InputStream>()

    @AfterEach
    fun closeStreams() {
        openStreams.forEach { runCatching { it.close() } }
        openStreams.clear()
    }

    @Test
    fun `SSE 스트림이 열려 있어도 참가자 수만큼 DB 커넥션을 점유하지 않는다`() {
        val fixture = seedParty()
        val baseline = awaitActiveConnectionsAtMost(IDLE_CONNECTIONS)

        repeat(STREAM_COUNT) { index -> openSseStream(fixture, nickname = "참가자$index") }
        assertEquals(STREAM_COUNT, openStreams.size, "SSE 스트림 ${STREAM_COUNT}개가 모두 열려 있어야 한다")

        val limit = baseline + ALLOWED_SLACK
        val active = awaitActiveConnectionsAtMost(limit)

        assertTrue(
            active <= limit,
            """
            SSE 스트림이 DB 커넥션을 점유하고 있다.
              열린 스트림: $STREAM_COUNT 개
              활성 커넥션: $active 개 (기준치 $baseline, 허용 상한 $limit)
            활성 커넥션이 스트림 수만큼 늘어났다면 spring.jpa.open-in-view 가 다시 켜졌는지 확인한다.
            """.trimIndent(),
        )
    }

    /**
     * SSE 스트림을 열고 첫 이벤트를 받을 때까지 기다린 뒤 스트림을 열어 둔 채로 반환한다.
     *
     * 첫 이벤트를 확인해야 입장 처리가 실제로 끝난 상태가 보장된다. 응답 헤더만 받고 단언하면
     * 서버가 아직 입장 트랜잭션을 처리 중일 수 있어 커넥션 수치가 불안정해진다.
     */
    private fun openSseStream(
        fixture: PartyFixture,
        nickname: String,
    ) {
        val streamUri =
            URI.create(
                "http://localhost:$port/api/v1/party-invites/" +
                    "${fixture.inviteToken}/realtime-participants/stream",
            )
        val request =
            HttpRequest
                .newBuilder(streamUri)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"nickname":"$nickname","characterId":${fixture.characterId}}""",
                    ),
                ).build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        assertEquals(200, response.statusCode(), "SSE 스트림 입장에 실패했다: nickname=$nickname")

        val body = response.body()
        openStreams.add(body)
        awaitFirstEvent(body, nickname)
    }

    private fun awaitFirstEvent(
        body: InputStream,
        nickname: String,
    ) {
        val reader = BufferedReader(body.reader())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(REQUEST_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val line = reader.readLine() ?: break
            if (line.startsWith("event:")) return
        }
        throw AssertionError("SSE 첫 이벤트를 받지 못했다: nickname=$nickname")
    }

    /** 활성 커넥션이 limit 이하로 떨어질 때까지 기다린 뒤 마지막 관측값을 돌려준다. */
    private fun awaitActiveConnectionsAtMost(limit: Int): Int {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS)
        var active = activeConnections()
        while (active > limit && System.nanoTime() < deadline) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
            active = activeConnections()
        }
        return active
    }

    private fun activeConnections(): Int =
        dataSource
            .unwrap(HikariDataSource::class.java)
            .hikariPoolMXBean
            .activeConnections

    private data class PartyFixture(
        val inviteToken: String,
        val characterId: Long,
    )

    private fun seedParty(): PartyFixture {
        val now = LocalDateTime.now()
        val character = characterRepository.save(Character(name = "테스트캐릭터-${UUID.randomUUID()}"))
        val party =
            partyRepository.save(
                RealtimeParty(ownerId = 1L, celebrantNickname = "생일자", startedAt = now.minusMinutes(1)),
            )
        val invite =
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    // PartyInvite.token 컬럼은 length=16 이라 UUID 원본(36자)을 그대로 쓰면 실패한다.
                    token =
                        UUID
                            .randomUUID()
                            .toString()
                            .replace("-", "")
                            .take(INVITE_TOKEN_LENGTH),
                    expiresAt = now.plusHours(1),
                ),
            )
        val hostParticipant =
            participantRepository.save(
                Participant(party = party, user = null, isCelebrant = true, hasWrittenPaper = false),
            )
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(participant = hostParticipant, nickname = "생일자"),
        )
        return PartyFixture(invite.token, character.id)
    }

    companion object {
        /** 풀 기본값(10)보다 작게 잡아, 회귀 시 커넥션 대기로 멈추지 않고 단언으로 실패하게 한다. */
        private const val STREAM_COUNT = 5

        /** 유휴 상태에서 허용하는 활성 커넥션 수. 기준치 측정이 배경 작업에 흔들리지 않게 한다. */
        private const val IDLE_CONNECTIONS = 1

        /** 스케줄러 등 배경 작업이 순간적으로 잡을 수 있는 커넥션 여유분. */
        private const val ALLOWED_SLACK = 1

        private const val AWAIT_SECONDS = 10L
        private const val POLL_INTERVAL_MILLIS = 100L
        private const val REQUEST_TIMEOUT_SECONDS = 20L
        private const val INVITE_TOKEN_LENGTH = 16
    }
}
