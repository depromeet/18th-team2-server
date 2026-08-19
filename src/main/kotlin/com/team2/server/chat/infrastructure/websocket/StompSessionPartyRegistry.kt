package com.team2.server.chat.infrastructure.websocket

import org.springframework.stereotype.Component

/**
 * STOMP 세션이 "실제로 입장 플로우를 통과한" 파티 목록을 세션 속성에 기록/조회한다.
 *
 * 세션 속성 맵은 WebSocket 세션당 하나이며 clientInboundChannel 스레드 풀에서
 * 동시 접근될 수 있으므로 맵 자체를 락으로 잡고 동기화된 Set 을 저장한다.
 */
@Component
class StompSessionPartyRegistry {
    fun markEntered(
        sessionAttributes: MutableMap<String, Any>?,
        partyId: Long,
    ) {
        if (sessionAttributes == null) return
        synchronized(sessionAttributes) {
            @Suppress("UNCHECKED_CAST")
            val enteredPartyIds =
                sessionAttributes.getOrPut(ENTERED_PARTY_IDS) {
                    java.util.Collections.synchronizedSet(mutableSetOf<Long>())
                } as MutableSet<Long>
            enteredPartyIds.add(partyId)
        }
    }

    fun hasEntered(
        sessionAttributes: Map<String, Any>?,
        partyId: Long,
    ): Boolean {
        if (sessionAttributes == null) return false
        synchronized(sessionAttributes) {
            val enteredPartyIds = sessionAttributes[ENTERED_PARTY_IDS] as? Set<*>
            return enteredPartyIds?.contains(partyId) == true
        }
    }

    companion object {
        private const val ENTERED_PARTY_IDS = "enteredPartyIds"
    }
}
