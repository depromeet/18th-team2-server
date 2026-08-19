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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class ChatSocketControllerTest {
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

    private lateinit var stompClient: WebSocketStompClient

    @BeforeEach
    fun setUp() {
        // 서버가 registerStompEndpoints("/ws")를 SockJS 없이 등록하므로 순수 WebSocket 클라이언트를 사용한다.
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun tearDown() {
        stompClient.stop()
    }

    @Test
    fun `WebSocket으로 입장하면 개인 entered 응답을 받는다`() {
        val fixture = seedParty()

        val session = connect()
        val entered = enter(session, fixture, nickname = "테스트유저").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals("entered", entered["event"])
        assertTrue((entered["data"] as Map<*, *>).containsKey("participantToken"))

        session.disconnect()
    }

    @Test
    fun `와일드카드 목적지 구독은 거부된다`() {
        val fixture = seedParty()

        // SimpleBroker의 DefaultSubscriptionRegistry는 구독 destination을 Ant 패턴으로 매칭하므로
        // 이 구독 하나로 모든 파티의 개인 ack(participantToken 포함)까지 수신할 수 있었다.
        val errorHandler = ErrorCapturingSessionHandler()
        val attacker = connect(errorHandler)
        val harvested = CompletableFuture<Map<String, Any>>()
        attacker.subscribe("/topic/parties/**", anyEventHandler(harvested))

        assertTrue(
            errorHandler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isNotBlank(),
            "와일드카드 구독은 ERROR 프레임으로 거부되어야 한다",
        )

        // 정상 입장이 일어나도 와일드카드 구독자에게는 아무것도 전달되지 않아야 한다.
        val victim = connect()
        enter(victim, fixture, nickname = "피해자").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue(harvested.isCompletedExceptionally || !harvested.isDone, "와일드카드 구독자는 프레임을 받지 못해야 한다")

        victim.disconnect()
    }

    @Test
    fun `입장하지 않은 파티의 브로드캐스트 토픽은 구독할 수 없다`() {
        val fixture = seedParty()

        // 파티 id는 순번이라 추측 가능하다. 입장 플로우를 거치지 않은 세션은 구독할 수 없어야 한다.
        val errorHandler = ErrorCapturingSessionHandler()
        val attacker = connect(errorHandler)
        attacker.subscribe("/topic/parties/${fixture.partyId}", anyEventHandler(CompletableFuture()))

        assertTrue(
            errorHandler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isNotBlank(),
            "입장하지 않은 세션의 브로드캐스트 구독은 거부되어야 한다",
        )
    }

    @Test
    fun `브로커 목적지로의 직접 전송은 거부된다`() {
        val fixture = seedParty()

        // SimpleBroker는 발신자를 구분하지 않으므로, 막지 않으면 누구나 파티 전원에게 위조 이벤트를 보낼 수 있다.
        val errorHandler = ErrorCapturingSessionHandler()
        val attacker = connect(errorHandler)
        attacker.send(
            "/topic/parties/${fixture.partyId}",
            mapOf(
                "event" to "user-entered",
                "data" to emptyMap<String, Any>(),
            ),
        )

        assertTrue(
            errorHandler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isNotBlank(),
            "브로커 목적지로의 직접 SEND는 거부되어야 한다",
        )
    }

    // --- 헬퍼 ---

    private data class PartyFixture(
        val partyId: Long,
        val inviteToken: String,
        val characterId: Long,
    )

    private fun seedParty(): PartyFixture {
        val now = LocalDateTime.now()
        // CharacterService.requireCharacter()가 characterId로 DB에서 실제 Character 존재 여부를 검증하므로
        // 하드코딩된 id 대신 실제로 저장한 Character의 id를 사용해야 한다.
        val character = characterRepository.save(Character(name = "테스트캐릭터-${UUID.randomUUID()}"))
        val party =
            partyRepository.save(
                RealtimeParty(ownerId = 1L, celebrantNickname = "생일자", startedAt = now.minusMinutes(1)),
            )
        val invite =
            partyInviteRepository.save(
                PartyInvite(
                    party = party,
                    // PartyInvite.token 컬럼은 length=16 — UUID 원본(36자)을 그대로 쓰면 길이 초과로 실패한다.
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
        // 필수로 조회하므로(RealtimePartyEndingInfoAdapter), 파티 생성 시 함께 만들어지는 호스트 참가자를
        // 미리 심어 둔다. 실제 파티 생성 플로우(PartyCreationService 등)를 그대로 재현하지는 않고,
        // 이 테스트가 요구하는 최소 상태만 구성한다.
        val hostParticipant =
            participantRepository.save(
                Participant(party = party, user = null, isCelebrant = true, hasWrittenPaper = false),
            )
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(participant = hostParticipant, nickname = "생일자"),
        )

        return PartyFixture(party.id, invite.token, character.id)
    }

    private fun connect(handler: StompSessionHandlerAdapter = object : StompSessionHandlerAdapter() {}): StompSession =
        stompClient
            .connectAsync("ws://localhost:$port/ws", handler)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /**
     * 개인 ack 채널을 먼저 구독한 뒤 입장 메시지를 전송한다.
     * 개인 채널은 입장 응답을 놓치지 않으려면 SEND 이전에 구독해야 하므로 입장 인가 대상이 아니며,
     * 대신 clientRequestId(UUID)로 추측 불가능성을 확보한다.
     */
    private fun enter(
        session: StompSession,
        fixture: PartyFixture,
        nickname: String,
        clientRequestId: String = UUID.randomUUID().toString(),
    ): CompletableFuture<Map<String, Any>> {
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
                if (body["event"] == eventName) {
                    future.complete(body)
                }
            }
        }

    private fun anyEventHandler(future: CompletableFuture<Map<String, Any>>): StompFrameHandler =
        object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

            @Suppress("UNCHECKED_CAST")
            override fun handleFrame(
                headers: StompHeaders,
                payload: Any?,
            ) {
                future.complete(payload as Map<String, Any>)
            }
        }

    /** 서버가 내려보내는 ERROR 프레임 / 전송 오류를 붙잡아 두는 세션 핸들러. */
    private class ErrorCapturingSessionHandler : StompSessionHandlerAdapter() {
        val error = CompletableFuture<String>()

        override fun getPayloadType(headers: StompHeaders): Type = String::class.java

        override fun handleFrame(
            headers: StompHeaders,
            payload: Any?,
        ) {
            error.complete(headers.getFirst("message") ?: payload?.toString() ?: "error")
        }

        override fun handleException(
            session: StompSession,
            command: StompCommand?,
            headers: StompHeaders,
            payload: ByteArray,
            exception: Throwable,
        ) {
            error.complete(exception.message ?: exception.javaClass.simpleName)
        }

        override fun handleTransportError(
            session: StompSession,
            exception: Throwable,
        ) {
            error.complete(exception.message ?: exception.javaClass.simpleName)
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L
    }
}
