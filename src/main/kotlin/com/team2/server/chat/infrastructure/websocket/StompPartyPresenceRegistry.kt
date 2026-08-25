package com.team2.server.chat.infrastructure.websocket

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket(STOMP) 세션을 통해 실제로 입장한 파티의 온라인 participantToken 을 전역으로 추적한다.
 *
 * [StompSessionPartyRegistry]는 세션 속성에 저장돼 세션당 로컬 조회만 가능하지만,
 * `/participants` 조회처럼 "파티 X 에 지금 누가 들어와 있나"를 전역으로 물어야 하는
 * 쓰임에는 세션-파티 매핑도 함께 들고 있어야 한다.
 */
@Component
class StompPartyPresenceRegistry {
    private val tokensByParty = ConcurrentHashMap<Long, MutableSet<String>>()
    private val entriesBySession = ConcurrentHashMap<String, MutableSet<PresenceEntry>>()

    fun markOnline(
        sessionId: String,
        partyId: Long,
        participantToken: String,
    ) {
        tokensByParty.computeIfAbsent(partyId) { ConcurrentHashMap.newKeySet() }.add(participantToken)
        entriesBySession
            .computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }
            .add(PresenceEntry(partyId, participantToken))
    }

    fun markOffline(
        sessionId: String,
        partyId: Long,
        participantToken: String,
    ) {
        tokensByParty[partyId]?.remove(participantToken)
        entriesBySession[sessionId]?.remove(PresenceEntry(partyId, participantToken))
    }

    @EventListener
    fun markSessionDisconnected(event: SessionDisconnectEvent) {
        markSessionDisconnected(event.sessionId)
    }

    fun markSessionDisconnected(sessionId: String) {
        val entries = entriesBySession.remove(sessionId) ?: return
        entries.forEach { entry -> tokensByParty[entry.partyId]?.remove(entry.participantToken) }
    }

    fun findOnlineParticipantTokens(partyId: Long): Set<String> = tokensByParty[partyId]?.toSet().orEmpty()

    private data class PresenceEntry(
        val partyId: Long,
        val participantToken: String,
    )
}
