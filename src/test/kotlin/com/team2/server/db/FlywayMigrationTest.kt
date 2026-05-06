package com.team2.server.db

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertEquals

class FlywayMigrationTest {
    @Test
    fun `Flyway migration creates schema and seeds default assets`() {
        DriverManager.getConnection(DATABASE_URL, USERNAME, PASSWORD).use { connection ->
            Flyway
                .configure()
                .dataSource(DATABASE_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            assertEquals(5, connection.countRows("avatar"))
            assertEquals(3, connection.countRows("rolling_paper_wrapper"))
            assertEquals(13, connection.countRows("image"))
        }
    }

    private fun java.sql.Connection.countRows(table: String): Int {
        require(table in COUNTABLE_TABLES) { "Unsupported table name: $table" }
        return createStatement().use { statement ->
            statement.executeQuery("select count(*) from $table").use { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }
    }

    private companion object {
        private val COUNTABLE_TABLES = setOf("avatar", "rolling_paper_wrapper", "image")
        private const val DATABASE_URL = "jdbc:tc:mysql:8.0:///flyway_migration_test"
        private const val USERNAME = "test"
        private const val PASSWORD = "test"
    }
}
