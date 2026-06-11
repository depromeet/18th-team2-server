package com.team2.server.burstgame.domain.policy

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.burstgame.domain.BurstGameRankingEntry

object BurstGameRankingPolicy {
    data class Score(
        val participant: BurstGameParticipantInfo,
        val tapCount: Int,
    )

    fun progressRankings(scores: Collection<Score>): List<BurstGameRankingEntry> =
        rankedScores(scores)
            .take(PROGRESS_RANKING_ENTRY_COUNT)
            .map { it.toRankingEntry() }

    fun finalRankings(scores: Collection<Score>): List<BurstGameRankingEntry> =
        rankedScores(scores)
            .map { it.toRankingEntry() }

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
            characterThumbnailImageUrl = score.participant.characterThumbnailImageUrl,
            role = score.participant.role,
            tapCount = score.tapCount,
        )

    private data class RankedScore(
        val rank: Int,
        val score: Score,
    )

    private const val PROGRESS_RANKING_ENTRY_COUNT = 3
}
