package com.team2.server.chat.service

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

    private fun isCompleted(emitter: SseEmitter): Boolean =
        try {
            emitter.send(emptySet<ResponseBodyEmitter.DataWithMediaType>())
            false
        } catch (_: IllegalStateException) {
            true
        } catch (_: java.io.IOException) {
            true
        }

    fun subscribe(
        partyId: Long,
        emitter: SseEmitter,
        participantToken: String,
    ) {
        emitters.computeIfAbsent(partyId) { CopyOnWriteArrayList() }.add(emitter)
        tokenToEmitter[participantToken] = emitter

        val remove =
            Runnable {
                remove(partyId, emitter)
                tokenToEmitter.remove(participantToken, emitter)
            }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove.run() }
    }

    fun unsubscribeByToken(participantToken: String) {
        tokenToEmitter.remove(participantToken)?.complete()
    }

    fun broadcast(
        partyId: Long,
        event: Set<ResponseBodyEmitter.DataWithMediaType>,
    ) {
        val list = emitters[partyId] ?: return
        val dead = list.filter { emitter -> !trySend(emitter, event) }
        list.removeAll(dead)
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

    fun count(partyId: Long): Int {
        val list = emitters[partyId] ?: return 0
        val dead = list.filter { isCompleted(it) }
        list.removeAll(dead)
        return list.size
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBroadcast(event: SseBroadcastEvent) {
        broadcast(event.partyId, event.event)
    }

    fun completeAll(partyId: Long) {
        emitters.remove(partyId)?.forEach { it.complete() }
    }

    private fun remove(
        partyId: Long,
        emitter: SseEmitter,
    ) {
        emitters[partyId]?.remove(emitter)
    }
}
