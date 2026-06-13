package com.team2.server.burstgame.domain.policy

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class BurstGameRankingPolicyTest {
    @Test
    fun `진행 중 순위는 dense ranking으로 계산하고 상위 3명만 포함한다`() {
        val rankings =
            BurstGameRankingPolicy.progressRankings(
                listOf(
                    score(1, 10),
                    score(2, 10),
                    score(3, 8),
                    score(4, 7),
                    score(5, 7),
                    score(6, 1),
                ),
            )

        assertEquals(listOf(1, 1, 2), rankings.map { it.rank })
        assertEquals(listOf(1L, 2L, 3L), rankings.map { it.participantId })
    }

    @Test
    fun `최종 순위는 상위 제한 없이 터치한 참가자 전체를 포함한다`() {
        val rankings =
            BurstGameRankingPolicy.finalRankings(
                listOf(
                    score(1, 10),
                    score(2, 10),
                    score(3, 8),
                    score(4, 7),
                    score(5, 7),
                    score(6, 1),
                ),
            )

        assertEquals(listOf(1, 1, 2, 3, 3, 4), rankings.map { it.rank })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), rankings.map { it.participantId })
    }

    @Test
    fun `최종 순위는 0회 참가자를 제외한다`() {
        val rankings =
            BurstGameRankingPolicy.finalRankings(
                listOf(
                    score(1, 10),
                    score(2, 0),
                    score(3, 5),
                ),
            )

        assertEquals(listOf(1L, 3L), rankings.map { it.participantId })
        assertEquals(listOf(1, 2), rankings.map { it.rank })
    }

    @Test
    fun `전원 0회면 rankings가 비어있다`() {
        val scores = listOf(score(1, 0), score(2, 0))

        assertEquals(emptyList(), BurstGameRankingPolicy.progressRankings(scores))
        assertEquals(emptyList(), BurstGameRankingPolicy.finalRankings(scores))
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
                    characterThumbnailImageUrl = null,
                    role = "PARTICIPANT",
                ),
            tapCount = tapCount,
        )
}
