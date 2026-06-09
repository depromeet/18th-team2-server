package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface PartyRepository : JpaRepository<Party, Long> {
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    fun findPartyById(id: Long): Party?

    fun existsByIdAndOwnerId(
        id: Long,
        ownerId: Long,
    ): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RealtimeParty party
        SET party.liveEndingStartedAt = :endingStartedAt,
            party.liveEndingReason = :endingReason
        WHERE party.id = :partyId
          AND party.liveEndingStartedAt IS NULL
        """,
    )
    fun startRealtimeEndingIfNotStarted(
        partyId: Long,
        endingStartedAt: LocalDateTime,
        endingReason: RealtimePartyEndingReason,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE RealtimeParty party
        SET party.hostEnteredAt = :hostEnteredAt
        WHERE party.id = :partyId
          AND party.hostEnteredAt IS NULL
        """,
    )
    fun markHostEnteredIfAbsent(
        partyId: Long,
        hostEnteredAt: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RealtimeParty party
        SET party.burstGameEndedAt = :endedAt
        WHERE party.id = :partyId
          AND party.burstGameEndedAt IS NULL
        """,
    )
    fun markBurstGameEndedIfAbsent(
        partyId: Long,
        endedAt: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            UPDATE realtime_party realtime_party
            JOIN party party ON party.id = realtime_party.id
            SET realtime_party.live_ending_started_at = DATE_ADD(
                party.started_at,
                INTERVAL :liveDurationMinutes MINUTE
            ),
                realtime_party.live_ending_reason = 'TIME_LIMIT_REACHED'
            WHERE realtime_party.live_ending_started_at IS NULL
              AND DATE_ADD(party.started_at, INTERVAL :liveDurationMinutes MINUTE) <= :now
              AND DATE_ADD(party.started_at, INTERVAL :partyEndedAfterDays DAY) > :now
            """,
        nativeQuery = true,
    )
    fun startAutomaticRealtimeEndings(
        now: LocalDateTime,
        liveDurationMinutes: Long,
        partyEndedAfterDays: Long,
    ): Int

    @Query(
        """
        SELECT party
        FROM RealtimeParty party
        WHERE party.liveEndingStartedAt IS NULL
          AND party.startedAt > :startedAfter
        """,
    )
    fun findRealtimePartiesWaitingAutomaticEnding(startedAfter: LocalDateTime): List<RealtimeParty>

    @Query(
        """
        SELECT party
        FROM RealtimeParty party
        WHERE party.liveEndingStartedAt IS NULL
          AND party.hostEnteredAt IS NOT NULL
          AND party.hostEnteredAt > :hostEnteredAfter
        """,
    )
    fun findRealtimePartiesWithHostEnteredAfter(hostEnteredAfter: LocalDateTime): List<RealtimeParty>

    @Query(
        """
        SELECT party
        FROM RealtimeParty party
        WHERE party.liveEndingStartedAt IS NOT NULL
          AND party.startedAt > :startedAfter
        """,
    )
    fun findRealtimePartiesWithEndingStarted(startedAfter: LocalDateTime): List<RealtimeParty>
}
