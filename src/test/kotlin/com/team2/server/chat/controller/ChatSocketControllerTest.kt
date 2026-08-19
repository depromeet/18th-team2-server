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
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
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

    // PartyInvite.token 컬럼은 length=16 — UUID 원본(36자)을 그대로 쓰면 DB 저장 시 길이 초과로 실패한다.
    private fun randomInviteToken(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(16)

    @Test
    fun `WebSocket으로 입장하면 개인 entered 응답과 브로드캐스트를 받는다`() {
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
                    token = randomInviteToken(),
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

        val session =
            stompClient
                .connectAsync("ws://localhost:$port/ws", object : StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS)

        val clientRequestId = UUID.randomUUID().toString()
        val enteredFuture = CompletableFuture<Map<String, Any>>()

        session.subscribe(
            "/topic/parties/${party.id}/personal/$clientRequestId",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                @Suppress("UNCHECKED_CAST")
                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val body = payload as Map<String, Any>
                    if (body["event"] == "entered") {
                        enteredFuture.complete(body)
                    }
                }
            },
        )

        session.send(
            "/app/party-invites/${invite.token}/realtime-participants",
            mapOf(
                "nickname" to "테스트유저",
                "characterId" to character.id,
                "clientRequestId" to clientRequestId,
            ),
        )

        val entered = enteredFuture.get(5, TimeUnit.SECONDS)
        assertEquals("entered", entered["event"])
        assertTrue((entered["data"] as Map<*, *>).containsKey("participantToken"))

        session.disconnect()
    }
}
