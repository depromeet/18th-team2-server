package com.team2.server.burstgame.domain

import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BurstGameSessionTest {
    private val startedAt = LocalDateTime.of(2026, 5, 14, 20, 10)
    private val session =
        BurstGameSession(
            partyId = 1L,
            startedAt = startedAt,
            endsAt = startedAt.plusSeconds(BurstGamePolicy.ROUND_DURATION_SECONDS),
        )

    @Test
    fun `accepted batch는 tap count와 stateVersion을 증가시킨다`() {
        val result = session.applyTap(participant(1), tapCount = 7, clientSequence = 1, now = startedAt.plusSeconds(1))

        assertTrue(result.accepted)
        assertEquals(7, result.snapshot.totalTapCount)
        assertEquals(7, result.snapshot.myTapCount)
        assertEquals(1, result.snapshot.stateVersion)
    }

    @Test
    fun `이미 처리한 clientSequence는 중복으로 무시하고 stateVersion을 증가시키지 않는다`() {
        session.applyTap(participant(1), tapCount = 7, clientSequence = 1, now = startedAt.plusSeconds(1))

        val duplicate =
            session.applyTap(participant(1), tapCount = 7, clientSequence = 1, now = startedAt.plusSeconds(2))

        assertFalse(duplicate.accepted)
        assertEquals(BurstGameTapIgnoredReason.DUPLICATE_SEQUENCE, duplicate.ignoredReason)
        assertEquals(7, duplicate.snapshot.totalTapCount)
        assertEquals(1, duplicate.snapshot.stateVersion)
    }

    @Test
    fun `처리되지 않은 낮은 sequence는 gap 범위 안이면 반영한다`() {
        session.applyTap(participant(1), tapCount = 3, clientSequence = 10, now = startedAt.plusSeconds(1))

        val late = session.applyTap(participant(1), tapCount = 2, clientSequence = 5, now = startedAt.plusSeconds(2))

        assertTrue(late.accepted)
        assertEquals(5, late.snapshot.totalTapCount)
        assertEquals(2, late.snapshot.stateVersion)
    }

    @Test
    fun `sequence gap이 너무 크면 INVALID_INPUT`() {
        val ex =
            assertThrows<BusinessException> {
                session.applyTap(
                    participant(1),
                    tapCount = 1,
                    clientSequence = BurstGamePolicy.MAX_SEQUENCE_GAP + 1,
                    now = startedAt.plusSeconds(1),
                )
            }

        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `tapCount와 clientSequence는 허용 범위 안의 값만 허용한다`() {
        val invalidTapCount =
            assertThrows<BusinessException> {
                session.applyTap(participant(1), tapCount = 0, clientSequence = 1, now = startedAt.plusSeconds(1))
            }
        val tooLargeTapCount =
            assertThrows<BusinessException> {
                session.applyTap(
                    participant(1),
                    tapCount = BurstGamePolicy.MAX_BATCH_TAP_COUNT.toInt() + 1,
                    clientSequence = 1,
                    now = startedAt.plusSeconds(1),
                )
            }
        val invalidSequence =
            assertThrows<BusinessException> {
                session.applyTap(participant(1), tapCount = 1, clientSequence = 0, now = startedAt.plusSeconds(1))
            }

        assertEquals(ErrorCode.INVALID_INPUT, invalidTapCount.errorCode)
        assertEquals(ErrorCode.INVALID_INPUT, tooLargeTapCount.errorCode)
        assertEquals(ErrorCode.INVALID_INPUT, invalidSequence.errorCode)
    }

    @Test
    fun `종료 시간이 시작 시간보다 늦어야 한다`() {
        assertThrows<IllegalArgumentException> {
            BurstGameSession(
                partyId = 1L,
                startedAt = startedAt,
                endsAt = startedAt,
            )
        }
    }

    @Test
    fun `tap result는 accepted와 ignoredReason 조합이 일관되어야 한다`() {
        val snapshot = session.snapshotFor(1, startedAt)

        assertThrows<IllegalArgumentException> {
            BurstGameTapResult(
                accepted = true,
                ignoredReason = BurstGameTapIgnoredReason.DUPLICATE_SEQUENCE,
                snapshot = snapshot,
            )
        }
        assertThrows<IllegalArgumentException> {
            BurstGameTapResult(accepted = false, ignoredReason = null, snapshot = snapshot)
        }
    }

    @Test
    fun `total tap count가 100 이상이면 colorChanged true`() {
        repeat(4) { index ->
            session.applyTap(
                participant(1),
                tapCount = 20,
                clientSequence = (index + 1).toLong(),
                now = startedAt.plusSeconds(index.toLong() + 1),
            )
        }
        session.applyTap(participant(1), tapCount = 20, clientSequence = 5, now = startedAt.plusSeconds(5))

        assertTrue(session.snapshotFor(1, startedAt.plusSeconds(5)).colorChanged)
    }

    @Test
    fun `종료 후 submit은 ROUND_ENDED로 무시한다`() {
        session.end(startedAt.plusSeconds(20))

        val result = session.applyTap(participant(1), tapCount = 1, clientSequence = 1, now = startedAt.plusSeconds(21))

        assertFalse(result.accepted)
        assertEquals(BurstGameTapIgnoredReason.ROUND_ENDED, result.ignoredReason)
    }

    @Test
    fun `종료 시간 이후 submit은 세션을 종료 상태로 전환한 뒤 ROUND_ENDED로 무시한다`() {
        val result = session.applyTap(participant(1), tapCount = 1, clientSequence = 1, now = startedAt.plusSeconds(20))

        assertFalse(result.accepted)
        assertTrue(result.endedNow)
        assertEquals(BurstGameTapIgnoredReason.ROUND_ENDED, result.ignoredReason)
        assertEquals(BurstGameRoundStatus.ENDED, result.snapshot.status)
    }

    @Test
    fun `종료 시간 전에는 세션을 종료할 수 없다`() {
        assertThrows<IllegalStateException> {
            session.end(startedAt.plusSeconds(19))
        }
    }

    @Test
    fun `종료 결과는 터치한 참가자 전체 rankings를 반환한다`() {
        session.applyTap(participant(1), tapCount = 10, clientSequence = 1, now = startedAt.plusSeconds(1))
        session.applyTap(participant(2), tapCount = 8, clientSequence = 1, now = startedAt.plusSeconds(1))

        val ended = session.end(startedAt.plusSeconds(20))

        assertEquals(listOf(1L, 2L), ended.rankings.map { it.participantId })
        assertEquals(listOf(1, 2), ended.rankings.map { it.rank })
    }

    private fun participant(participantId: Long): BurstGameParticipantInfo =
        BurstGameParticipantInfo(
            participantId = participantId,
            nickname = "p$participantId",
            characterId = null,
            characterImageUrl = null,
            role = "PARTICIPANT",
        )
}
