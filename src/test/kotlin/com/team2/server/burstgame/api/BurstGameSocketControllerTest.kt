package com.team2.server.burstgame.api

import com.team2.server.burstgame.application.port.BurstGameSessionStore
import com.team2.server.burstgame.domain.BurstGameSession
import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import com.team2.server.common.DatabaseCleanup
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class BurstGameSocketControllerTest {
    @LocalServerPort
    private var port: Int = 0

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

    @Autowired
    private lateinit var sessionStore: BurstGameSessionStore

    @Autowired
    private lateinit var databaseCleanup: DatabaseCleanup

    private lateinit var stompClient: WebSocketStompClient

    @BeforeEach
    fun setUp() {
        sessionStore.clear()
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun tearDown() {
        stompClient.stop()
        databaseCleanup.execute()
        sessionStore.clear()
    }

    @Test
    fun `WebSocket으로 탭을 제출하면 개인 ack로 결과를 받는다`() {
        val fixture = seedParty()
        startPlayableRound(fixture.partyId)

        val session = connect()
        val entered = enter(session, fixture, nickname = "손님").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val participantToken = participantTokenOf(entered)

        val ack = submitTaps(session, fixture.partyId, participantToken, tapCount = 7, clientSequence = 1)

        val response = await(ack.ack, ack.error)
        val data = response["data"] as Map<*, *>
        assertEquals(true, data["accepted"])
        assertEquals(7, (data["myTapCount"] as Number).toInt())
        assertEquals(7, (data["totalTapCount"] as Number).toInt())

        session.disconnect()
    }

    @Test
    fun `라운드 시작 전 탭 제출은 ROUND_NOT_STARTED로 무시된 ack를 받는다`() {
        val fixture = seedParty()
        startCountdownRound(fixture.partyId)

        val session = connect()
        val entered = enter(session, fixture, nickname = "손님").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val participantToken = participantTokenOf(entered)

        val ack = submitTaps(session, fixture.partyId, participantToken, tapCount = 7, clientSequence = 1)

        val response = await(ack.ack, ack.error)
        val data = response["data"] as Map<*, *>
        assertEquals(false, data["accepted"])
        assertEquals("ROUND_NOT_STARTED", data["ignoredReason"])

        session.disconnect()
    }

    @Test
    fun `WebSocket으로 탭을 제출하면 다른 참가자에게 진행 상황이 브로드캐스트된다`() {
        val fixture = seedParty()
        startPlayableRound(fixture.partyId)

        val observer = connect()
        val observerEntered = enter(observer, fixture, nickname = "지켜보는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val broadcasts = subscribeBurstGameBroadcast(observer, fixture, participantTokenOf(observerEntered))
        val progressFuture =
            broadcasts.expect("burst-game-progress") { data ->
                (data["totalTapCount"] as Number).toInt() >= LARGE_TAP_COUNT
            }

        val tapper = connect()
        val tapperEntered = enter(tapper, fixture, nickname = "탭하는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        submitTapsAndAwait(tapper, fixture.partyId, participantTokenOf(tapperEntered), LARGE_TAP_COUNT, 1)

        val broadcast = progressFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue((broadcast["totalTapCount"] as Number).toInt() >= LARGE_TAP_COUNT)

        observer.disconnect()
        tapper.disconnect()
    }

    // --- 헬퍼 ---

    private data class PartyFixture(
        val partyId: Long,
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
                    token =
                        UUID
                            .randomUUID()
                            .toString()
                            .replace("-", "")
                            .take(16),
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

        return PartyFixture(party.id, invite.token, character.id)
    }

    private fun startPlayableRound(partyId: Long) {
        val now = LocalDateTime.now()
        val startedAt = now.minusSeconds(1)
        sessionStore.start(partyId, now) {
            BurstGameSession(
                partyId = partyId,
                startedAt = startedAt,
                endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
            )
        }
    }

    private fun startCountdownRound(partyId: Long) {
        val now = LocalDateTime.now()
        val startedAt = now.plusMinutes(1)
        sessionStore.start(partyId, now) {
            BurstGameSession(
                partyId = partyId,
                startedAt = startedAt,
                endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
            )
        }
    }

    private fun connect(): StompSession =
        stompClient
            .connectAsync("ws://localhost:$port/ws", object : StompSessionHandlerAdapter() {})
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    private fun enter(
        session: StompSession,
        fixture: PartyFixture,
        nickname: String,
    ): CompletableFuture<Map<String, Any>> {
        val clientRequestId = UUID.randomUUID().toString()
        val enteredFuture = CompletableFuture<Map<String, Any>>()
        session.subscribe(
            "/topic/parties/${fixture.partyId}/personal/$clientRequestId",
            eventHandler("entered", enteredFuture),
        )
        session.send(
            "/app/party-invites/${fixture.inviteToken}/realtime-participants",
            mapOf(
                "nickname" to nickname,
                "characterId" to fixture.characterId,
                "clientRequestId" to clientRequestId,
            ),
        )
        return enteredFuture
    }

    private fun participantTokenOf(entered: Map<String, Any>): String =
        (entered["data"] as Map<*, *>)["participantToken"] as String

    private fun submitTaps(
        session: StompSession,
        partyId: Long,
        participantToken: String,
        tapCount: Int,
        clientSequence: Long,
    ): SocketRequest {
        val clientRequestId = UUID.randomUUID().toString()
        val ack = CompletableFuture<Map<String, Any>>()
        val error = CompletableFuture<Map<String, Any>>()
        session.subscribe(
            "/topic/parties/$partyId/personal/$clientRequestId",
            eventHandler("tap-submitted", ack),
        )
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", error))
        session.send(
            "/app/parties/$partyId/burst-game/taps",
            mapOf(
                "tapCount" to tapCount,
                "clientSequence" to clientSequence,
                "participantToken" to participantToken,
                "clientRequestId" to clientRequestId,
            ),
        )
        return SocketRequest(ack, error)
    }

    private fun submitTapsAndAwait(
        session: StompSession,
        partyId: Long,
        participantToken: String,
        tapCount: Int,
        clientSequence: Long,
    ) {
        val request = submitTaps(session, partyId, participantToken, tapCount, clientSequence)
        await(request.ack, request.error)
    }

    /**
     * 브로드캐스트 토픽을 구독하고, 구독이 실제로 브로커에 등록될 때까지 기다린다.
     *
     * SimpleBroker 는 SUBSCRIBE 에 RECEIPT 를 돌려주지 않아 구독 등록 완료를 직접 확인할 방법이
     * 없다. 대신 스스로 브로드캐스트(탭 제출)를 유발해 되돌아올 때까지 재시도한다.
     */
    private fun subscribeBurstGameBroadcast(
        session: StompSession,
        fixture: PartyFixture,
        participantToken: String,
    ): BroadcastCollector {
        val collector = BroadcastCollector()
        session.subscribe("/topic/parties/${fixture.partyId}", collector)

        repeat(PROBE_ATTEMPTS) { attempt ->
            val probe = collector.expect("burst-game-progress") { true }
            submitTapsAndAwait(session, fixture.partyId, participantToken, 1, (attempt + 1).toLong())
            if (completedWithin(probe, PROBE_INTERVAL_MILLIS)) return collector
        }
        fail("브로드캐스트 구독이 준비되지 않았습니다: partyId=${fixture.partyId}")
    }

    private fun completedWithin(
        future: CompletableFuture<*>,
        millis: Long,
    ): Boolean = runCatching { future.get(millis, TimeUnit.MILLISECONDS) }.isSuccess

    private fun await(
        target: CompletableFuture<Map<String, Any>>,
        vararg failures: CompletableFuture<Map<String, Any>>,
    ): Map<String, Any> {
        CompletableFuture
            .anyOf(target, *failures)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!target.isDone) {
            val failure = failures.first { it.isDone }.get()
            val data = failure["data"] as Map<*, *>
            fail("WebSocket 요청이 실패했습니다: code=${data["code"]}, message=${data["message"]}")
        }
        return target.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private class SocketRequest(
        val ack: CompletableFuture<Map<String, Any>>,
        val error: CompletableFuture<Map<String, Any>>,
    )

    private class BroadcastCollector : StompFrameHandler {
        private class Expectation(
            val eventName: String,
            val predicate: (Map<*, *>) -> Boolean,
            val future: CompletableFuture<Map<String, Any>>,
        )

        private val expectations = CopyOnWriteArrayList<Expectation>()

        fun expect(
            eventName: String,
            predicate: (Map<*, *>) -> Boolean = { true },
        ): CompletableFuture<Map<String, Any>> {
            val future = CompletableFuture<Map<String, Any>>()
            expectations.add(Expectation(eventName, predicate, future))
            return future
        }

        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

        @Suppress("UNCHECKED_CAST")
        override fun handleFrame(
            headers: StompHeaders,
            payload: Any?,
        ) {
            val body = payload as Map<String, Any>
            if (body["event"] !is String) return
            val data = body["data"] as? Map<*, *> ?: return
            expectations
                .filter { it.eventName == body["event"] && it.predicate(data) }
                .forEach { it.future.complete(data as Map<String, Any>) }
        }
    }

    private fun eventHandler(
        eventName: String,
        future: CompletableFuture<Map<String, Any>>,
    ): StompFrameHandler =
        object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

            @Suppress("UNCHECKED_CAST")
            override fun handleFrame(
                headers: StompHeaders,
                payload: Any?,
            ) {
                val body = payload as Map<String, Any>
                if (body["event"] == eventName) future.complete(body)
            }
        }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val PROBE_INTERVAL_MILLIS = 400L
        const val PROBE_ATTEMPTS = 10
        const val LARGE_TAP_COUNT = 20
    }
}
