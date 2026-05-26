package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.BurstGameRankingEntry
import io.swagger.v3.oas.annotations.media.Schema

data class BurstGameRankingResponse(
    @Schema(description = "공동 순위를 허용하는 현재 순위입니다.", example = "1")
    val rank: Int,
    @Schema(description = "랭킹에 표시할 실시간 파티 참여자 ID입니다.", example = "37")
    val participantId: Long,
    @Schema(description = "실시간 파티 프로필 닉네임입니다.", example = "토끼왕")
    val nickname: String,
    @Schema(description = "선택한 캐릭터 ID입니다.", example = "2", nullable = true)
    val characterId: Long?,
    @Schema(description = "선택한 캐릭터 이미지 URL입니다.", example = "https://example.com/rabbit.png", nullable = true)
    val characterImageUrl: String?,
    @Schema(description = "실시간 파티 참여자 역할입니다.", example = "CELEBRANT", allowableValues = ["CELEBRANT", "PARTICIPANT"])
    val role: String,
    @Schema(description = "해당 참여자의 현재 라운드 누적 터치 수입니다.", example = "11")
    val tapCount: Int,
) {
    companion object {
        fun from(entry: BurstGameRankingEntry): BurstGameRankingResponse =
            BurstGameRankingResponse(
                rank = entry.rank,
                participantId = entry.participantId,
                nickname = entry.nickname,
                characterId = entry.characterId,
                characterImageUrl = entry.characterImageUrl,
                role = entry.role,
                tapCount = entry.tapCount,
            )
    }
}
