package com.team2.server.chat.service

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitterRegistry {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    private fun isCompleted(emitter: SseEmitter): Boolean {
        return try {
            emitter.send(emptySet<ResponseBodyEmitter.DataWithMediaType>())
            false
        } catch (_: IllegalStateException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    fun subscribe(partyId: Long, emitter: SseEmitter) {
        emitters.getOrPut(partyId) { CopyOnWriteArrayList() }.add(emitter)

        val remove = Runnable { remove(partyId, emitter) }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove.run() }
    }

    fun broadcast(partyId: Long, event: Set<ResponseBodyEmitter.DataWithMediaType>) {
        val list = emitters[partyId] ?: return
        val dead = mutableListOf<SseEmitter>()
        for (emitter in list) {
            try {
                emitter.send(event)
            } catch (e: Exception) {
                dead.add(emitter)
            }
        }
        list.removeAll(dead)
    }

    fun count(partyId: Long): Int {
        val list = emitters[partyId] ?: return 0
        val dead = list.filter { isCompleted(it) }
        list.removeAll(dead)
        return list.size
    }

    private fun remove(partyId: Long, emitter: SseEmitter) {
        emitters[partyId]?.remove(emitter)
    }
}
