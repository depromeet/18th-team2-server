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
        SET party.liveStartedAt = :liveStartedAt
        WHERE party.id = :partyId
          AND party.liveStartedAt IS NULL
        """,
    )
    fun markLiveStartedIfAbsent(
        partyId: Long,
        liveStartedAt: LocalDateTime,
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

    // 주의: SET 절과 WHERE 절의 COALESCE(...) 마감 시각 계산식은 반드시 동일하게 유지해야 한다.
    // SET 절은 실제로 찍히는 종료 시각을, WHERE 절은 대상 row 선택 기준을 계산하는데,
    // 한쪽만 수정하면 선택 기준과 저장되는 값이 어긋나는 버그가 조용히 발생한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value =
            """
            UPDATE realtime_party realtime_party
            JOIN party party ON party.id = realtime_party.id
            SET realtime_party.live_ending_started_at = COALESCE(
                    DATE_ADD(realtime_party.live_started_at, INTERVAL :liveDurationMinutes MINUTE),
                    DATE_ADD(party.started_at, INTERVAL :startGraceMinutes MINUTE)
                ),
                realtime_party.live_ending_reason = :endingReason
            WHERE realtime_party.live_ending_started_at IS NULL
              AND COALESCE(
                    DATE_ADD(realtime_party.live_started_at, INTERVAL :liveDurationMinutes MINUTE),
                    DATE_ADD(party.started_at, INTERVAL :startGraceMinutes MINUTE)
                  ) <= :now
              AND DATE_ADD(party.started_at, INTERVAL :partyEndedAfterDays DAY) > :now
            """,
        nativeQuery = true,
    )
    fun startAutomaticRealtimeEndings(
        now: LocalDateTime,
        liveDurationMinutes: Long,
        startGraceMinutes: Long,
        partyEndedAfterDays: Long,
        endingReason: String,
    ): Int

    @Query(
        """
        SELECT party
        FROM RealtimeParty party
        WHERE party.liveEndingStartedAt IS NULL
          AND party.startedAt >= :startedAfter
        """,
    )
    fun findRealtimePartiesWaitingAutomaticEnding(startedAfter: LocalDateTime): List<RealtimeParty>

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
