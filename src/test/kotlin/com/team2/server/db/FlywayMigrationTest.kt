package com.team2.server.db

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import kotlin.test.assertEquals

class FlywayMigrationTest {
    @Test
    fun `Flyway migration backfills existing realtime party ending reasons`() {
        val flywayToV7 =
            Flyway
                .configure()
                .dataSource(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password)
                .locations("classpath:db/migration")
                .target("7")
                .cleanDisabled(false)
                .load()
        flywayToV7.clean()
        flywayToV7.migrate()

        DriverManager.getConnection(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    insert into users (
                        id, created_at, updated_at, name, birth_day, provider, provider_id, email
                    ) values (
                        1, '2026-06-08 19:00:00', '2026-06-08 19:00:00',
                        'host', '01-01', 'KAKAO', 'provider-id', 'host@example.com'
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    insert into party (
                        id, party_option, created_at, updated_at, owner_id, started_at
                    ) values
                        (1, 'REALTIME', '2026-06-08 19:00:00', '2026-06-08 19:00:00', 1, '2026-06-08 20:00:00'),
                        (2, 'REALTIME', '2026-06-08 19:00:00', '2026-06-08 19:00:00', 1, '2026-06-08 20:00:00')
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    insert into realtime_party (id, live_ending_started_at, host_entered_at) values
                        (1, '2026-06-08 20:05:00', '2026-06-08 20:00:00'),
                        (2, '2026-06-08 20:10:00', '2026-06-08 20:00:00')
                    """.trimIndent(),
                )
            }

            Flyway
                .configure()
                .dataSource(MYSQL.jdbcUrl, MYSQL.username, MYSQL.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            assertEquals("HOST_REQUEST", connection.findEndingReason(1L))
            assertEquals("TIME_LIMIT_REACHED", connection.findEndingReason(2L))
        }
    }

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
            assertEquals(18, connection.countRows("image"))
            assertEquals(0, connection.countRollingPaperToppingsMissingImage())
            assertEquals(
                ColumnDefinition(dataType = "datetime", datetimePrecision = 6, nullable = true),
                connection.findColumn("realtime_party", "live_ending_started_at"),
            )
            assertEquals(
                ColumnDefinition(dataType = "datetime", datetimePrecision = 6, nullable = true),
                connection.findColumn("realtime_party", "host_entered_at"),
            )
            assertEquals(
                ColumnDefinition(dataType = "varchar", datetimePrecision = 0, nullable = true),
                connection.findColumn("realtime_party", "live_ending_reason"),
            )
            assertEquals(
                ColumnDefinition(dataType = "datetime", datetimePrecision = 6, nullable = true),
                connection.findColumn("realtime_party", "burst_game_ended_at"),
            )
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

    private fun java.sql.Connection.findEndingReason(partyId: Long): String =
        prepareStatement("select live_ending_reason from realtime_party where id = ?").use { statement ->
            statement.setLong(1, partyId)
            statement.executeQuery().use { resultSet ->
                resultSet.next()
                resultSet.getString(1)
            }
        }

    private fun java.sql.Connection.countRollingPaperToppingsMissingImage(): Int =
        createStatement().use { statement ->
            statement
                .executeQuery(
                    """
                    select count(*)
                    from rolling_paper_wrapper rpw
                    where not exists (
                        select 1
                        from image i
                        where i.target_type = 'ROLLING_PAPER_WRAPPER'
                          and i.target_id = rpw.id
                          and i.image_url is not null
                    )
                    """.trimIndent(),
                ).use { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                }
        }

    private companion object {
        private val COUNTABLE_TABLES = setOf("avatar", "rolling_paper_wrapper", "image")
        private val TABLES_WITH_COLUMNS_TO_ASSERT = setOf("realtime_party")

        @JvmStatic
        private val MYSQL: MySQLContainer<*> =
            MySQLContainer(DockerImageName.parse("mysql:8.0"))
                .withLabel("purpose", "flyway-migration-test")
                .withReuse(true)
                .also { it.start() }
    }

    private fun java.sql.Connection.findColumn(
        table: String,
        column: String,
    ): ColumnDefinition {
        require(table in TABLES_WITH_COLUMNS_TO_ASSERT) { "Unsupported table name: $table" }
        return prepareStatement(
            """
            select data_type, datetime_precision, is_nullable
            from information_schema.columns
            where table_schema = database()
              and table_name = ?
              and column_name = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, table)
            statement.setString(2, column)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    throw NoSuchElementException("Column not found: $table.$column")
                }

                ColumnDefinition(
                    dataType = resultSet.getString("data_type"),
                    datetimePrecision = resultSet.getInt("datetime_precision"),
                    nullable = resultSet.getString("is_nullable") == "YES",
                )
            }
        }
    }

    private data class ColumnDefinition(
        val dataType: String,
        val datetimePrecision: Int,
        val nullable: Boolean,
    )
}
