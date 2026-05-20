package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.BurstGameTapIgnoredReason
import com.team2.server.burstgame.domain.BurstGameTapResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class SubmitBurstGameTapResponse(
    @Schema(description = "박터뜨리기 라운드가 속한 파티 ID입니다.", example = "10")
    val partyId: Long,
    @Schema(description = "요청한 사용자의 실시간 파티 참여자 ID입니다.", example = "37")
    val myParticipantId: Long,
    @Schema(description = "이번 터치 batch가 집계에 반영되었는지 여부입니다.", example = "true")
    val accepted: Boolean,
    @Schema(
        description = "터치 batch가 반영되지 않은 이유입니다. accepted=true이면 null입니다.",
        allowableValues = ["DUPLICATE_SEQUENCE", "ROUND_ENDED"],
        nullable = true,
    )
    val ignoredReason: BurstGameTapIgnoredReason?,
    @Schema(description = "현재까지 반영된 전체 터치 수입니다.", example = "42")
    val totalTapCount: Int,
    @Schema(description = "요청한 사용자의 현재 라운드 누적 터치 수입니다.", example = "11")
    val myTapCount: Int,
    @Schema(description = "전체 터치 수가 색상 변경 기준에 도달했는지 여부입니다.", example = "false")
    val colorChanged: Boolean,
    @Schema(description = "라운드 상태 변경 버전입니다. 실제 반영된 tap 또는 종료 전이마다 증가합니다.", example = "13")
    val stateVersion: Long,
    @Schema(description = "응답 생성 시점의 서버 시각입니다.", example = "2026-05-14T20:10:07.120")
    val serverTime: LocalDateTime,
    @Schema(description = "진행 중 상태에서 제공되는 상위 3개 rank group입니다.")
    val rankings: List<BurstGameRankingResponse>,
) {
    companion object {
        fun from(result: BurstGameTapResult): SubmitBurstGameTapResponse {
            val snapshot = result.snapshot
            return SubmitBurstGameTapResponse(
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                accepted = result.accepted,
                ignoredReason = result.ignoredReason,
                totalTapCount = snapshot.totalTapCount,
                myTapCount = snapshot.myTapCount,
                colorChanged = snapshot.colorChanged,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
                rankings = snapshot.rankings.map { BurstGameRankingResponse.from(it) },
            )
        }
    }
}
