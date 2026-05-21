package com.team2.server.burstgame.domain.policy

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRankingEntry
import com.team2.server.burstgame.domain.BurstGameWinner

object BurstGameRankingPolicy {
    data class Score(
        val participant: BurstGameParticipantInfo,
        val tapCount: Int,
    )

    fun rankings(scores: Collection<Score>): List<BurstGameRankingEntry> {
        val ranked = rankedScores(scores)
        val topRanks =
            ranked
                .map { it.rank }
                .distinct()
                .take(TOP_RANK_GROUP_COUNT)
                .toSet()
        return ranked
            .filter { it.rank in topRanks }
            .map { it.toRankingEntry() }
    }

    fun winners(scores: Collection<Score>): List<BurstGameWinner> {
        val ranked = rankedScores(scores)
        return ranked
            .filter { it.rank == WINNER_RANK }
            .map {
                BurstGameWinner(
                    participantId = it.score.participant.participantId,
                    nickname = it.score.participant.nickname,
                    characterId = it.score.participant.characterId,
                    characterImageUrl = it.score.participant.characterImageUrl,
                    role = it.score.participant.role,
                    tapCount = it.score.tapCount,
                )
            }
    }

    private fun rankedScores(scores: Collection<Score>): List<RankedScore> {
        var currentRank = 0
        var previousTapCount: Int? = null
        return scores
            .filter { it.tapCount > 0 }
            .sortedWith(compareByDescending<Score> { it.tapCount }.thenBy { it.participant.participantId })
            .map { score ->
                if (previousTapCount != score.tapCount) {
                    currentRank += 1
                    previousTapCount = score.tapCount
                }
                RankedScore(currentRank, score)
            }
    }

    private fun RankedScore.toRankingEntry(): BurstGameRankingEntry =
        BurstGameRankingEntry(
            rank = rank,
            participantId = score.participant.participantId,
            nickname = score.participant.nickname,
            characterId = score.participant.characterId,
            characterImageUrl = score.participant.characterImageUrl,
            role = score.participant.role,
            tapCount = score.tapCount,
        )

    private data class RankedScore(
        val rank: Int,
        val score: Score,
    )

    private const val TOP_RANK_GROUP_COUNT = 3
    private const val WINNER_RANK = 1
}
