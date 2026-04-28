package com.team2.server.auth

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import java.lang.reflect.Field
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

class FakeUserRepository : UserRepository {
    private val store = mutableMapOf<Long, User>()
    private val seq = AtomicLong(1L)

    override fun findByProviderAndProviderId(
        provider: AuthProvider,
        providerId: String,
    ): User? = store.values.firstOrNull { it.provider == provider && it.providerId == providerId }

    override fun <S : User> save(entity: S): S {
        if (entity.id == 0L) {
            val newId = seq.getAndIncrement()
            val idField: Field = entity.javaClass.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(entity, newId)
        }
        store[entity.id] = entity
        return entity
    }

    override fun findById(id: Long): Optional<User> = Optional.ofNullable(store[id])

    override fun findAll(): MutableList<User> = store.values.toMutableList()

    override fun count(): Long = store.size.toLong()

    fun all(): List<User> = store.values.toList()

    override fun <S : User> saveAll(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()

    override fun flush() = Unit

    override fun <S : User> saveAndFlush(entity: S): S = save(entity)

    override fun <S : User> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()

    override fun deleteAllInBatch(entities: MutableIterable<User>) = Unit

    override fun deleteAllByIdInBatch(ids: MutableIterable<Long>) = Unit

    override fun deleteAllInBatch() = store.clear()

    override fun getOne(id: Long): User = store.getValue(id)

    override fun getById(id: Long): User = store.getValue(id)

    override fun getReferenceById(id: Long): User = store.getValue(id)

    override fun existsById(id: Long): Boolean = store.containsKey(id)

    override fun findAllById(ids: MutableIterable<Long>): MutableList<User> =
        ids.mapNotNull { store[it] }.toMutableList()

    override fun findAll(sort: Sort): MutableList<User> = findAll()

    override fun findAll(pageable: Pageable): Page<User> = throw NotImplementedError()

    override fun deleteById(id: Long) {
        store.remove(id)
    }

    override fun delete(entity: User) {
        store.remove(entity.id)
    }

    override fun deleteAllById(ids: MutableIterable<Long>) {
        ids.forEach { store.remove(it) }
    }

    override fun deleteAll(entities: MutableIterable<User>) {
        entities.forEach { delete(it) }
    }

    override fun deleteAll() {
        store.clear()
    }

    override fun <S : User> findOne(example: Example<S>): Optional<S> = throw NotImplementedError()

    override fun <S : User> findAll(example: Example<S>): MutableList<S> = throw NotImplementedError()

    override fun <S : User> findAll(
        example: Example<S>,
        sort: Sort,
    ): MutableList<S> = throw NotImplementedError()

    override fun <S : User> findAll(
        example: Example<S>,
        pageable: Pageable,
    ): Page<S> = throw NotImplementedError()

    override fun <S : User> count(example: Example<S>): Long = 0

    override fun <S : User> exists(example: Example<S>): Boolean = false

    override fun <S : User, R : Any?> findBy(
        example: Example<S>,
        queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>,
    ): R = throw NotImplementedError()
}
