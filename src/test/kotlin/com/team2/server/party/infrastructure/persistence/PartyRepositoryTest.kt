package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.domain.entity.RealtimePartyEndingReason
import com.team2.server.support.JpaSliceTestSupport
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PartyRepositoryTest
    @Autowired
    constructor(
        private val partyRepository: PartyRepository,
        private val entityManager: EntityManager,
    ) : JpaSliceTestSupport() {
        @Test
        fun `startRealtimeEndingIfNotStarted는 종료 시각과 사유를 함께 저장한다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))

            val updated =
                partyRepository.startRealtimeEndingIfNotStarted(
                    party.id,
                    BASE_TIME,
                    RealtimePartyEndingReason.HOST_LEFT,
                )
            entityManager.flush()
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, updated)
            assertEquals(BASE_TIME, found.liveEndingStartedAt)
            assertEquals(RealtimePartyEndingReason.HOST_LEFT, found.liveEndingReason)
        }

        @Test
        fun `startAutomaticRealtimeEndings는 시작된 파티를 시작 10분 후로 종료한다`() {
            val liveStartedAt = BASE_TIME.minusMinutes(10)
            val party =
                partyRepository.save(
                    realtimeParty(startedAt = BASE_TIME.minusMinutes(20), liveStartedAt = liveStartedAt),
                )

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, updated)
            assertEquals(liveStartedAt.plusMinutes(10), found.liveEndingStartedAt)
        }

        @Test
        fun `startAutomaticRealtimeEndings는 미시작 파티를 마감선에 종료한다`() {
            val startedAt = BASE_TIME.minusMinutes(30)
            val party = partyRepository.save(realtimeParty(startedAt = startedAt))

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, updated)
            assertEquals(startedAt.plusMinutes(30), found.liveEndingStartedAt)
            assertEquals(RealtimePartyEndingReason.TIME_LIMIT_REACHED, found.liveEndingReason)
        }

        @Test
        fun `startAutomaticRealtimeEndings는 마감선 전 미시작 파티를 종료하지 않는다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(29)))

            val updated =
                partyRepository.startAutomaticRealtimeEndings(
                    now = BASE_TIME,
                    liveDurationMinutes = 10,
                    startGraceMinutes = 30,
                    partyEndedAfterDays = 7,
                    endingReason = RealtimePartyEndingReason.TIME_LIMIT_REACHED.name,
                )
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(0, updated)
            assertEquals(null, found.liveEndingStartedAt)
        }

        @Test
        fun `markBurstGameEndedIfAbsent는 최초 박터뜨리기 종료 시각만 저장한다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))
            val firstEndedAt = BASE_TIME
            val secondEndedAt = BASE_TIME.plusSeconds(1)

            val firstUpdated = partyRepository.markBurstGameEndedIfAbsent(party.id, firstEndedAt)
            val secondUpdated = partyRepository.markBurstGameEndedIfAbsent(party.id, secondEndedAt)
            entityManager.flush()
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, firstUpdated)
            assertEquals(0, secondUpdated)
            assertEquals(firstEndedAt, found.burstGameEndedAt)
        }

        @Test
        fun `markLiveStartedIfAbsent는 liveStartedAt을 한 번만 저장한다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))
            val firstLiveStartedAt = BASE_TIME
            val secondLiveStartedAt = BASE_TIME.plusSeconds(5)

            val firstUpdated = partyRepository.markLiveStartedIfAbsent(party.id, firstLiveStartedAt)
            val secondUpdated = partyRepository.markLiveStartedIfAbsent(party.id, secondLiveStartedAt)
            entityManager.flush()
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, firstUpdated)
            assertEquals(0, secondUpdated)
            assertEquals(firstLiveStartedAt, found.liveStartedAt)
        }

        private fun realtimeParty(
            startedAt: LocalDateTime,
            liveEndingStartedAt: LocalDateTime? = null,
            liveStartedAt: LocalDateTime? = null,
        ): RealtimeParty =
            RealtimeParty(
                ownerId = 1L,
                name = "테스트파티",
                startedAt = startedAt,
                liveEndingStartedAt = liveEndingStartedAt,
                liveStartedAt = liveStartedAt,
            )

        private companion object {
            val BASE_TIME: LocalDateTime = LocalDateTime.of(2026, 5, 24, 20, 0)
        }
    }
