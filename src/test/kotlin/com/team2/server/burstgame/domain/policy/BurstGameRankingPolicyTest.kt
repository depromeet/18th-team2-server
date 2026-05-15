package com.team2.server.burstgame.domain.policy

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class BurstGameRankingPolicyTest {
    @Test
    fun `동점은 dense ranking으로 계산하고 상위 3개 rank group을 모두 포함한다`() {
        val rankings =
            BurstGameRankingPolicy.rankings(
                listOf(
                    score(1, 10),
                    score(2, 10),
                    score(3, 8),
                    score(4, 7),
                    score(5, 7),
                    score(6, 1),
                ),
            )

        assertEquals(listOf(1, 1, 2, 3, 3), rankings.map { it.rank })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), rankings.map { it.participantId })
    }

    @Test
    fun `공동 1등은 winners에 모두 포함한다`() {
        val winners =
            BurstGameRankingPolicy.winners(
                listOf(
                    score(2, 10),
                    score(1, 10),
                    score(3, 8),
                ),
            )

        assertEquals(listOf(1L, 2L), winners.map { it.participantId })
        assertEquals(listOf(10, 10), winners.map { it.tapCount })
    }

    @Test
    fun `전원 0회면 rankings와 winners가 비어있다`() {
        val scores = listOf(score(1, 0), score(2, 0))

        assertEquals(emptyList(), BurstGameRankingPolicy.rankings(scores))
        assertEquals(emptyList(), BurstGameRankingPolicy.winners(scores))
    }

    private fun score(
        participantId: Long,
        tapCount: Int,
    ): BurstGameRankingPolicy.Score =
        BurstGameRankingPolicy.Score(
            participant =
                BurstGameParticipantInfo(
                    participantId = participantId,
                    nickname = "p$participantId",
                    characterId = null,
                    characterImageUrl = null,
                    role = "PARTICIPANT",
                ),
            tapCount = tapCount,
        )
}
