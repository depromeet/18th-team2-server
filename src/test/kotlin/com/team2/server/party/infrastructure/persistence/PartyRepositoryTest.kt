package com.team2.server.party.infrastructure.persistence

import com.team2.server.party.domain.entity.RealtimeParty
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
        fun `markHostEnteredIfAbsent는 hostEnteredAt을 한 번만 저장한다`() {
            val party = partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))
            val firstHostEnteredAt = BASE_TIME
            val secondHostEnteredAt = BASE_TIME.plusSeconds(5)

            val firstUpdated = partyRepository.markHostEnteredIfAbsent(party.id, firstHostEnteredAt)
            val secondUpdated = partyRepository.markHostEnteredIfAbsent(party.id, secondHostEnteredAt)
            entityManager.flush()
            entityManager.clear()

            val found = partyRepository.findById(party.id).orElseThrow() as RealtimeParty
            assertEquals(1, firstUpdated)
            assertEquals(0, secondUpdated)
            assertEquals(firstHostEnteredAt, found.hostEnteredAt)
        }

        @Test
        fun `findRealtimePartiesWithHostEnteredAfter는 진행 중이고 주최자 입장 시각이 기준 이후인 파티만 찾는다`() {
            val hostEnteredAfter = BASE_TIME.minusMinutes(5)
            val target =
                partyRepository.save(
                    realtimeParty(
                        startedAt = BASE_TIME.minusMinutes(1),
                        hostEnteredAt = BASE_TIME,
                    ),
                )
            partyRepository.save(
                realtimeParty(
                    startedAt = BASE_TIME.minusMinutes(10),
                    hostEnteredAt = hostEnteredAfter.minusNanos(1),
                ),
            )
            partyRepository.save(realtimeParty(startedAt = BASE_TIME.minusMinutes(1)))
            partyRepository.save(
                realtimeParty(
                    startedAt = BASE_TIME.minusMinutes(1),
                    liveEndingStartedAt = BASE_TIME.minusSeconds(30),
                    hostEnteredAt = BASE_TIME,
                ),
            )
            entityManager.flush()
            entityManager.clear()

            val result = partyRepository.findRealtimePartiesWithHostEnteredAfter(hostEnteredAfter)

            assertEquals(listOf(target.id), result.map { it.id })
        }

        private fun realtimeParty(
            startedAt: LocalDateTime,
            liveEndingStartedAt: LocalDateTime? = null,
            hostEnteredAt: LocalDateTime? = null,
        ): RealtimeParty =
            RealtimeParty(
                ownerId = 1L,
                name = "테스트파티",
                startedAt = startedAt,
                liveEndingStartedAt = liveEndingStartedAt,
                hostEnteredAt = hostEnteredAt,
            )

        private companion object {
            val BASE_TIME: LocalDateTime = LocalDateTime.of(2026, 5, 24, 20, 0)
        }
    }
