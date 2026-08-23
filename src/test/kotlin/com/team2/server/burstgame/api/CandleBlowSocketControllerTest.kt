package com.team2.server.burstgame.api

import com.team2.server.burstgame.application.port.CandleBlowSessionStore
import com.team2.server.burstgame.application.usecase.StartCandleBlowUseCase
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
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class CandleBlowSocketControllerTest {
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
    private lateinit var startCandleBlowUseCase: StartCandleBlowUseCase

    @Autowired
    private lateinit var candleBlowSessionStore: CandleBlowSessionStore

    @Autowired
    private lateinit var databaseCleanup: DatabaseCleanup

    private lateinit var stompClient: WebSocketStompClient

    @BeforeEach
    fun setUp() {
        candleBlowSessionStore.clear()
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun tearDown() {
        stompClient.stop()
        databaseCleanup.execute()
        candleBlowSessionStore.clear()
    }

    @Test
    fun `WebSocket으로 촛불을 끄면 개인 ack로 현재 상태를 받는다`() {
        val fixture = seedParty()
        startCandleBlowUseCase(fixture.partyId, LocalDateTime.now())

        val session = connect()
        val entered = enter(session, fixture, nickname = "손님").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val participantToken = participantTokenOf(entered)

        val ack = blowCandle(session, fixture.partyId, participantToken, candleId = 3)

        val response = await(ack.ack, ack.error)
        val data = response["data"] as Map<*, *>
        assertEquals(fixture.partyId.toInt(), (data["partyId"] as Number).toInt())
        assertEquals("ACTIVE", data["status"])
        val candles = data["candles"] as List<*>
        val thirdCandle = candles[2] as Map<*, *>
        assertEquals(3, (thirdCandle["candleId"] as Number).toInt())
        assertEquals(true, thirdCandle["extinguished"])

        session.disconnect()
    }

    @Test
    fun `촛불끄기가 시작되지 않았으면 에러 채널로 실패가 통지된다`() {
        val fixture = seedParty()

        val session = connect()
        val entered = enter(session, fixture, nickname = "손님").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val participantToken = participantTokenOf(entered)

        val ack = blowCandle(session, fixture.partyId, participantToken, candleId = 1)

        val error = ack.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("CANDLE_BLOW_NOT_STARTED", (error["data"] as Map<*, *>)["code"])
        assertTrue(!ack.ack.isDone, "촛불끄기 시작 전 요청은 개인 ack가 오지 않아야 한다")

        session.disconnect()
    }

    @Test
    fun `WebSocket으로 촛불을 끄면 다른 참가자에게 진행 상황이 브로드캐스트된다`() {
        val fixture = seedParty()
        startCandleBlowUseCase(fixture.partyId, LocalDateTime.now())

        val observer = connect()
        val observerEntered = enter(observer, fixture, nickname = "지켜보는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val broadcasts = subscribeCandleBlowBroadcast(observer, fixture, participantTokenOf(observerEntered))
        val progressFuture =
            broadcasts.expect("candle-blow-progress") { data ->
                val candles = data["candles"] as List<*>
                (candles[4] as Map<*, *>)["extinguished"] == true
            }

        val blower = connect()
        val blowerEntered = enter(blower, fixture, nickname = "촛불끄는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        blowCandleAndAwait(blower, fixture, participantTokenOf(blowerEntered), candleId = 5)

        val broadcast = progressFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val candles = (broadcast["data"] as Map<*, *>)["candles"] as List<*>
        val fifthCandle = candles[4] as Map<*, *>
        assertEquals(true, fifthCandle["extinguished"])

        observer.disconnect()
        blower.disconnect()
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
        // ResolveRealtimePartyEndingInfoUseCase가 셀러브런트(host) RealtimeParticipantProfile을
        // 필수로 조회하므로, 파티 생성 시 함께 만들어지는 호스트 참가자를 미리 심어 둔다.
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

    /**
     * 브로드캐스트 토픽을 구독하고, 구독이 실제로 브로커에 등록될 때까지 기다린다.
     *
     * SimpleBroker 는 SUBSCRIBE 에 RECEIPT 를 돌려주지 않아 구독 등록 완료를 직접 확인할 방법이
     * 없다. 대신 스스로 브로드캐스트(촛불 끄기)를 유발해 되돌아올 때까지 재시도한다 — 이미 꺼진
     * 촛불은 다시 꺼도 브로드캐스트가 나가지 않으므로(no-op), 시도마다 다른 촛불을 쓴다.
     */
    private fun subscribeCandleBlowBroadcast(
        session: StompSession,
        fixture: PartyFixture,
        participantToken: String,
    ): BroadcastCollector {
        val collector = BroadcastCollector()
        session.subscribe("/topic/parties/${fixture.partyId}", collector)

        for (probeCandleId in PROBE_CANDLE_IDS) {
            val probe =
                collector.expect("candle-blow-progress") { data ->
                    val candles = data["candles"] as List<*>
                    (candles[probeCandleId - 1] as Map<*, *>)["extinguished"] == true
                }
            blowCandleAndAwait(session, fixture, participantToken, probeCandleId)
            if (completedWithin(probe, PROBE_INTERVAL_MILLIS)) return collector
        }
        fail("브로드캐스트 구독이 준비되지 않았습니다: partyId=${fixture.partyId}")
    }

    private fun completedWithin(
        future: CompletableFuture<*>,
        millis: Long,
    ): Boolean = runCatching { future.get(millis, TimeUnit.MILLISECONDS) }.isSuccess

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
                .forEach { it.future.complete(body) }
        }
    }

    private fun blowCandle(
        session: StompSession,
        partyId: Long,
        participantToken: String,
        candleId: Int,
    ): SocketRequest {
        val clientRequestId = UUID.randomUUID().toString()
        val ack = CompletableFuture<Map<String, Any>>()
        val error = CompletableFuture<Map<String, Any>>()
        session.subscribe(
            "/topic/parties/$partyId/personal/$clientRequestId",
            eventHandler("candle-blown", ack),
        )
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", error))
        session.send(
            "/app/parties/$partyId/candle-blow/candles/$candleId",
            mapOf("participantToken" to participantToken, "clientRequestId" to clientRequestId),
        )
        return SocketRequest(ack, error)
    }

    private fun blowCandleAndAwait(
        session: StompSession,
        fixture: PartyFixture,
        participantToken: String,
        candleId: Int,
    ) {
        val request = blowCandle(session, fixture.partyId, participantToken, candleId)
        await(request.ack, request.error)
    }

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

    private fun eventHandler(
        eventName: String,
        future: CompletableFuture<Map<String, Any>>,
        dataPredicate: (Map<*, *>) -> Boolean = { true },
    ): StompFrameHandler =
        object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

            @Suppress("UNCHECKED_CAST")
            override fun handleFrame(
                headers: StompHeaders,
                payload: Any?,
            ) {
                val body = payload as Map<String, Any>
                if (body["event"] == eventName && dataPredicate(body["data"] as Map<*, *>)) {
                    future.complete(body)
                }
            }
        }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val PROBE_INTERVAL_MILLIS = 250L
        val PROBE_CANDLE_IDS = listOf(9, 8, 7, 6, 4, 3, 2, 1)
    }
}
