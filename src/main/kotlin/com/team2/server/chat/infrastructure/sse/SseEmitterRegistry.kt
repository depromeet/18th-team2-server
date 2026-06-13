package com.team2.server.chat.infrastructure.sse

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitterRegistry {
    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()
    private val tokenToEmitter = ConcurrentHashMap<String, SseEmitter>()
    private val emitterToToken = ConcurrentHashMap<SseEmitter, String>()
    private val emitterToPartyId = ConcurrentHashMap<SseEmitter, Long>()

    fun subscribe(
        partyId: Long,
        emitter: SseEmitter,
        participantToken: String,
    ) {
        tokenToEmitter.put(participantToken, emitter)?.also { oldEmitter ->
            emitters[partyId]?.remove(oldEmitter)
            emitterToToken.remove(oldEmitter)
            emitterToPartyId.remove(oldEmitter)
            oldEmitter.complete()
        }
        emitterToToken[emitter] = participantToken
        emitterToPartyId[emitter] = partyId
        emitters.computeIfAbsent(partyId) { CopyOnWriteArrayList() }.add(emitter)

        val remove =
            Runnable {
                remove(partyId, emitter)
                tokenToEmitter.remove(participantToken, emitter)
                emitterToToken.remove(emitter)
                emitterToPartyId.remove(emitter)
            }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove.run() }
    }

    fun unsubscribeByToken(participantToken: String) {
        tokenToEmitter.remove(participantToken)?.also { emitter ->
            emitterToToken.remove(emitter)
            removeEmitterFromParty(emitter)
            emitter.complete()
        }
    }

    fun broadcast(
        partyId: Long,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
        excludeToken: String? = null,
    ) {
        val list = emitters[partyId] ?: return
        val excludedEmitter = excludeToken?.let { tokenToEmitter[it] }
        val dead =
            list
                .asSequence()
                .filter { emitter -> emitter !== excludedEmitter }
                .filter { emitter -> !trySend(emitter, event) }
                .toList()
        list.removeAll(dead)
        removeTokenMappings(dead)
    }

    private fun trySend(
        emitter: SseEmitter,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
    ): Boolean =
        try {
            emitter.send(event)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: java.io.IOException) {
            false
        }

    private fun removeTokenMappings(dead: List<SseEmitter>) {
        dead.forEach { deadEmitter ->
            val token = emitterToToken.remove(deadEmitter)
            if (token != null) {
                tokenToEmitter.remove(token, deadEmitter)
            }
            emitterToPartyId.remove(deadEmitter)
        }
    }

    fun count(partyId: Long): Int = emitters[partyId]?.size ?: 0

    fun findParticipantTokens(partyId: Long): Set<String> =
        emitters[partyId]
            .orEmpty()
            .mapNotNull { emitterToToken[it] }
            .toSet()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBroadcast(event: SseBroadcastEvent) {
        broadcast(event.partyId, event.event, event.excludeToken)
    }

    fun completeAll(partyId: Long) {
        val removed = emitters.remove(partyId).orEmpty()
        removeTokenMappings(removed)
        removed.forEach { it.complete() }
    }

    private fun remove(
        partyId: Long,
        emitter: SseEmitter,
    ) {
        emitters[partyId]?.remove(emitter)
    }

    private fun removeEmitterFromParty(emitter: SseEmitter) {
        val partyId = emitterToPartyId.remove(emitter) ?: return
        emitters[partyId]?.remove(emitter)
    }
}
