package com.team2.server.db

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals

class FlywayMigrationTest {
    @Test
    fun `Flyway migration creates schema and seeds default assets`() {
        DriverManager.getConnection(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password).use { connection ->
            Flyway
                .configure()
                .dataSource(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .also { it.clean() }
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

        @JvmStatic
        private val MYSQL: MySQLContainer<*> =
            MySQLContainer(DockerImageName.parse("mysql:8.0"))
                .withLabel("purpose", "flyway-migration-test")
                .withReuse(true)
                .also { it.start() }
    }
}
