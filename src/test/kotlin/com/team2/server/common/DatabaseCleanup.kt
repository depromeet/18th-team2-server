package com.team2.server.common

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.Table
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DatabaseCleanup(
    @PersistenceContext
    private val entityManager: EntityManager,
) {
    private lateinit var tableNames: List<String>

    @jakarta.annotation.PostConstruct
    fun init() {
        tableNames =
            entityManager.metamodel.entities
                .filter { it.javaType.isAnnotationPresent(Table::class.java) }
                .map { it.javaType.getAnnotation(Table::class.java).name }
                .filter { it.isNotBlank() } +
            entityManager.metamodel.entities
                .filter { !it.javaType.isAnnotationPresent(Table::class.java) }
                .map { it.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase() }
    }

    @Transactional
    fun execute() {
        entityManager.flush()
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate()
        try {
            for (tableName in tableNames) {
                entityManager.createNativeQuery("TRUNCATE TABLE `$tableName`").executeUpdate()
            }
        } finally {
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate()
        }
    }
}
