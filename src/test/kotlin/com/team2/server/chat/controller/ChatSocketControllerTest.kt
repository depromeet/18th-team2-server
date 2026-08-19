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
    fun `WebSocket으로 입장하면 개인 entered 응답과 다른 참가자에게 브로드캐스트가 전달된다`() {
        val fixture = seedParty()

        // 먼저 입장해 있는 참가자(session1). 입장에 성공해야 브로드캐스트 토픽 구독이 인가된다.
        val session1 = connect()
        enter(session1, fixture, nickname = "먼저온유저").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val broadcastFuture = CompletableFuture<Map<String, Any>>()
        session1.subscribe(
            "/topic/parties/${fixture.partyId}",
            // 개인 ack("entered")는 커밋 이전에, "user-entered" 브로드캐스트는 커밋 이후에 전송된다.
            // 따라서 session1이 ack를 받고 구독을 붙이는 시점에 따라 자기 자신의 입장 브로드캐스트를
            // 받을 수도 있다. 닉네임으로 걸러 session2의 입장만 확인해야 테스트가 결정적이다.
            eventHandler("user-entered", broadcastFuture) { data -> data["nickname"] == "나중온유저" },
        )

        // 뒤이어 입장하는 참가자(session2)
        val session2 = connect()
        val entered = enter(session2, fixture, nickname = "나중온유저").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals("entered", entered["event"])
        assertTrue((entered["data"] as Map<*, *>).containsKey("participantToken"))

        // session1은 session2의 입장을 브로드캐스트로 수신해야 한다.
        val broadcast = broadcastFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("user-entered", broadcast["event"])
        assertEquals("나중온유저", (broadcast["data"] as Map<*, *>)["nickname"])

        session1.disconnect()
        session2.disconnect()
    }

    @Test
    fun `만료된 초대로 입장하면 에러 채널로 실패가 통지된다`() {
        val fixture = seedParty(inviteExpired = true)

        val session = connect()
        val clientRequestId = UUID.randomUUID().toString()
        val errorFuture = CompletableFuture<Map<String, Any>>()
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", errorFuture))
        // 개인 ack 채널도 함께 구독해 두고, 실패 시 그쪽으로는 아무것도 오지 않음을 확인한다.
        val enteredFuture = enter(session, fixture, nickname = "만료유저", clientRequestId = clientRequestId)

        val error = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("error", error["event"])
        assertEquals("INVITE_LINK_EXPIRED", (error["data"] as Map<*, *>)["code"])
        assertTrue(!enteredFuture.isDone, "입장에 실패하면 entered ack는 오지 않아야 한다")

        session.disconnect()
    }

    @Test
    fun `닉네임이 비어 있으면 입장이 거부된다`() {
        val fixture = seedParty()

        val session = connect()
        val clientRequestId = UUID.randomUUID().toString()
        val errorFuture = CompletableFuture<Map<String, Any>>()
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", errorFuture))
        val enteredFuture = enter(session, fixture, nickname = "  ", clientRequestId = clientRequestId)

        val error = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("INVALID_INPUT", (error["data"] as Map<*, *>)["code"])
        assertTrue(!enteredFuture.isDone, "유효성 검증에 실패하면 입장 처리가 되지 않아야 한다")

        session.disconnect()
    }

    @Test
    fun `닉네임이 20자를 초과하면 입장이 거부된다`() {
        val fixture = seedParty()

        val session = connect()
        val clientRequestId = UUID.randomUUID().toString()
        val errorFuture = CompletableFuture<Map<String, Any>>()
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", errorFuture))
        val enteredFuture = enter(session, fixture, nickname = "가".repeat(21), clientRequestId = clientRequestId)

        val error = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("INVALID_INPUT", (error["data"] as Map<*, *>)["code"])
        assertTrue(!enteredFuture.isDone, "유효성 검증에 실패하면 입장 처리가 되지 않아야 한다")

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

    @Test
    fun `WebSocket으로 보낸 메시지가 파티 참가자에게 브로드캐스트된다`() {
        val fixture = seedParty()

        val receiver = connect()
        enter(receiver, fixture, nickname = "수신자").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val messageFuture = CompletableFuture<Map<String, Any>>()
        receiver.subscribe("/topic/parties/${fixture.partyId}", eventHandler("message", messageFuture))

        val sender = connect()
        val entered = enter(sender, fixture, nickname = "발신자").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        sendMessage(sender, fixture.partyId, participantTokenOf(entered), "안녕하세요!")

        val broadcast = messageFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val data = broadcast["data"] as Map<*, *>
        assertEquals("안녕하세요!", data["content"])
        assertEquals("발신자", data["senderNickname"])

        receiver.disconnect()
        sender.disconnect()
    }

    @Test
    fun `입장하지 않은 파티로 메시지를 보내면 에러 채널로 실패가 통지된다`() {
        val myParty = seedParty()
        val othersParty = seedParty()

        // participantToken 은 발급받은 파티에서만 유효해야 한다. 다른 파티로 그대로 보내면 거부된다.
        val session = connect()
        val entered = enter(session, myParty, nickname = "침입자").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val clientRequestId = UUID.randomUUID().toString()
        val errorFuture = CompletableFuture<Map<String, Any>>()
        session.subscribe("/topic/errors/$clientRequestId", eventHandler("error", errorFuture))
        sendMessage(session, othersParty.partyId, participantTokenOf(entered), "남의 파티", clientRequestId)

        val error = errorFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("PARTY_FORBIDDEN", (error["data"] as Map<*, *>)["code"])

        session.disconnect()
    }

    @Test
    fun `WebSocket으로 퇴장하면 남은 참가자에게 user-left가 브로드캐스트된다`() {
        val fixture = seedParty()

        val stayer = connect()
        enter(stayer, fixture, nickname = "남는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val leftFuture = CompletableFuture<Map<String, Any>>()
        stayer.subscribe("/topic/parties/${fixture.partyId}", eventHandler("user-left", leftFuture))

        val leaver = connect()
        val entered = enter(leaver, fixture, nickname = "떠나는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        leaveParty(leaver, fixture.partyId, participantTokenOf(entered))

        val broadcast = leftFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals("떠나는사람", (broadcast["data"] as Map<*, *>)["nickname"])

        stayer.disconnect()
        leaver.disconnect()
    }

    @Test
    fun `퇴장한 세션은 파티 브로드캐스트 토픽을 다시 구독할 수 없다`() {
        val fixture = seedParty()

        // 퇴장 처리가 끝난 시점을 관측하기 위해 다른 세션이 user-left 브로드캐스트를 기다린다.
        val observer = connect()
        enter(observer, fixture, nickname = "관찰자").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val leftFuture = CompletableFuture<Map<String, Any>>()
        observer.subscribe("/topic/parties/${fixture.partyId}", eventHandler("user-left", leftFuture))

        val errorHandler = ErrorCapturingSessionHandler()
        val leaver = connect(errorHandler)
        val entered = enter(leaver, fixture, nickname = "떠나는사람").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        leaveParty(leaver, fixture.partyId, participantTokenOf(entered))
        leftFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        leaver.subscribe("/topic/parties/${fixture.partyId}", anyEventHandler(CompletableFuture()))

        assertTrue(
            errorHandler.error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isNotBlank(),
            "퇴장한 세션의 브로드캐스트 재구독은 거부되어야 한다",
        )

        observer.disconnect()
    }

    // --- 헬퍼 ---

    private data class PartyFixture(
        val partyId: Long,
        val inviteToken: String,
        val characterId: Long,
    )

    private fun seedParty(inviteExpired: Boolean = false): PartyFixture {
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
                    expiresAt = if (inviteExpired) now.minusHours(1) else now.plusHours(1),
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

    private fun participantTokenOf(entered: Map<String, Any>): String =
        (entered["data"] as Map<*, *>)["participantToken"] as String

    private fun sendMessage(
        session: StompSession,
        partyId: Long,
        participantToken: String,
        content: String,
        clientRequestId: String = UUID.randomUUID().toString(),
    ) {
        session.send(
            "/app/parties/$partyId/chat-messages",
            mapOf(
                "content" to content,
                "participantToken" to participantToken,
                "clientRequestId" to clientRequestId,
            ),
        )
    }

    private fun leaveParty(
        session: StompSession,
        partyId: Long,
        participantToken: String,
        clientRequestId: String = UUID.randomUUID().toString(),
    ) {
        session.send(
            "/app/parties/$partyId/leave",
            mapOf(
                "participantToken" to participantToken,
                "clientRequestId" to clientRequestId,
            ),
        )
    }

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
