package com.team2.server.burstgame.application.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.team2.server.burstgame.domain.BurstGameRoundStatus
import com.team2.server.burstgame.domain.BurstGameSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BurstGameStateResponse(
    @Schema(description = "박터뜨리기 라운드가 속한 파티 ID입니다.", example = "10")
    val partyId: Long,
    @Schema(description = "요청한 사용자의 실시간 파티 참여자 ID입니다.", example = "37")
    val myParticipantId: Long,
    @Schema(description = "박터뜨리기 라운드 종료 여부입니다.", example = "false")
    val ended: Boolean,
    @Schema(description = "서버 기준 라운드 시작 시각입니다.", example = "2026-05-14T20:10:00")
    val startedAt: LocalDateTime,
    @Schema(description = "서버 기준 라운드 종료 시각입니다.", example = "2026-05-14T20:10:20")
    val endsAt: LocalDateTime,
    @Schema(description = "종료 상태에서 확정된 전체 터치 수입니다. 진행 중 상태에서는 내려주지 않습니다.", example = "137", nullable = true)
    val totalTapCount: Int?,
    @Schema(description = "요청한 사용자의 현재 라운드 누적 터치 수입니다.", example = "11")
    val myTapCount: Int,
    @Schema(description = "전체 터치 수가 색상 변경 기준에 도달했는지 여부입니다.", example = "false")
    val colorChanged: Boolean,
    @Schema(description = "라운드 상태 변경 버전입니다. 실제 반영된 tap 또는 종료 전이마다 증가합니다.", example = "13")
    val stateVersion: Long,
    @Schema(description = "응답 생성 시점의 서버 시각입니다.", example = "2026-05-14T20:10:07.120")
    val serverTime: LocalDateTime,
    @Schema(description = "서버 기준 남은 실제 플레이 시간입니다. 카운트다운 중에는 20, 종료 상태에서는 0입니다.", example = "13")
    val remainingSeconds: Long,
    @Schema(description = "진행 중에는 상위 3명, 종료 상태에서는 1회 이상 터치한 참가자 전체 최종 순위입니다.")
    val rankings: List<BurstGameRankingResponse>,
) {
    companion object {
        fun from(snapshot: BurstGameSnapshot): BurstGameStateResponse =
            BurstGameStateResponse(
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                ended = snapshot.status.isEnded(),
                startedAt = snapshot.startedAt,
                endsAt = snapshot.endsAt,
                totalTapCount = snapshot.totalTapCount.takeIf { snapshot.status.isEnded() },
                myTapCount = snapshot.myTapCount,
                colorChanged = snapshot.colorChanged,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
                remainingSeconds = snapshot.remainingSeconds,
                rankings = snapshot.rankings.map { BurstGameRankingResponse.from(it) },
            )

        private fun BurstGameRoundStatus.isEnded(): Boolean = this == BurstGameRoundStatus.ENDED
    }
}
