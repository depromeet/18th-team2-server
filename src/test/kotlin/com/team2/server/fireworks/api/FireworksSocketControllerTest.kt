package com.team2.server.fireworks.api

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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class FireworksSocketControllerTest {
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
    private lateinit var databaseCleanup: DatabaseCleanup

    private lateinit var stompClient: WebSocketStompClient

    @BeforeEach
    fun setUp() {
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun tearDown() {
        stompClient.stop()
        databaseCleanup.execute()
    }

    @Test
    fun `WebSocket으로 폭죽을 트리거하면 개인 ack를 받는다`() {
        val fixture = seedParty()

        val session = connect()
        val entered = enter(session, fixture, nickname = "손님").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val participantToken = participantTokenOf(entered)

        val ack = triggerFireworks(session, fixture.partyId, participantToken)

        val response = await(ack.ack, ack.error)
        val data = response["data"] as Map<*, *>
        assertEquals(fixture.partyId.toInt(), (data["partyId"] as Number).toInt())
        assertEquals("손님", data["nickname"])

        session.disconnect()
    }

    @Test
    fun `WebSocket으로 폭죽을 트리거하면 다른 참가자에게 브로드캐스트된다`() {
        val fixture = seedParty()

        val observer = connect()
        val observerEntered = enter(observer, fixture, nickname = "지켜보는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val broadcasts = subscribeFireworksBroadcast(observer, fixture, participantTokenOf(observerEntered))
        val fireworksFuture = broadcasts.expect("fireworks") { data -> data["nickname"] == "터뜨리는사람" }

        val trigger = connect()
        val triggerEntered = enter(trigger, fixture, nickname = "터뜨리는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        triggerFireworksAndAwait(trigger, fixture.partyId, participantTokenOf(triggerEntered))

        val broadcast = fireworksFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("터뜨리는사람", broadcast["nickname"])

        observer.disconnect()
        trigger.disconnect()
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

    private fun triggerFireworks(
        session: StompSession,
        partyId: Long,
        participantToken: String,
    ): SocketRequest {
        val clientRequestId = UUID.randomUUID().toString()
        val ack = CompletableFuture<Map<String, Any>>()
        val error = CompletableFuture<Map<String, Any>>()
        session.subscribe(
            "/topic/parties/$partyId/personal/$clientRequestId",
            eventHandler("fireworks-triggered", ack),
        )
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", error))
        session.send(
            "/app/parties/$partyId/fireworks",
            mapOf("participantToken" to participantToken, "clientRequestId" to clientRequestId),
        )
        return SocketRequest(ack, error)
    }

    private fun triggerFireworksAndAwait(
        session: StompSession,
        partyId: Long,
        participantToken: String,
    ) {
        val request = triggerFireworks(session, partyId, participantToken)
        await(request.ack, request.error)
    }

    /**
     * 브로드캐스트 토픽을 구독하고, 구독이 실제로 브로커에 등록될 때까지 기다린다.
     *
     * SimpleBroker 는 SUBSCRIBE 에 RECEIPT 를 돌려주지 않아 구독 등록 완료를 직접 확인할 방법이
     * 없다. 대신 스스로 폭죽을 트리거해 되돌아올 때까지 재시도한다. 프로브 자신의 이벤트는
     * nickname 필터로 걸러 실제 대상(트리거하는 사람)의 이벤트와 섞이지 않게 한다.
     */
    private fun subscribeFireworksBroadcast(
        session: StompSession,
        fixture: PartyFixture,
        participantToken: String,
    ): BroadcastCollector {
        val collector = BroadcastCollector()
        session.subscribe("/topic/parties/${fixture.partyId}", collector)

        val probe = collector.expect("fireworks") { data -> data["nickname"] == "지켜보는사람" }
        triggerFireworksAndAwait(session, fixture.partyId, participantToken)
        if (completedWithin(probe, PROBE_TIMEOUT_MILLIS)) return collector
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

        private val expectations = java.util.concurrent.CopyOnWriteArrayList<Expectation>()

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
        const val PROBE_TIMEOUT_MILLIS = 5_000L
    }
}
