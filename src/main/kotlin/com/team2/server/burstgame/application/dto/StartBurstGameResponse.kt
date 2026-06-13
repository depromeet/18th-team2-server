package com.team2.server.burstgame.application.dto

import com.team2.server.burstgame.domain.BurstGameSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class StartBurstGameResponse(
    @Schema(description = "박터뜨리기 라운드가 속한 파티 ID입니다.", example = "10")
    val partyId: Long,
    @Schema(description = "요청한 사용자의 실시간 파티 참여자 ID입니다.", example = "37")
    val myParticipantId: Long,
    @Schema(description = "서버 기준 라운드 시작 시각입니다.", example = "2026-05-14T20:10:00")
    val startedAt: LocalDateTime,
    @Schema(description = "서버 기준 라운드 종료 시각입니다.", example = "2026-05-14T20:10:20")
    val endsAt: LocalDateTime,
    @Schema(description = "현재 라운드의 전체 누적 터치 수입니다.", example = "0")
    val totalTapCount: Int,
    @Schema(description = "라운드 상태 변경 버전입니다. 실제 반영된 tap 또는 종료 전이마다 증가합니다.", example = "0")
    val stateVersion: Long,
    @Schema(description = "응답 생성 시점의 서버 시각입니다.", example = "2026-05-14T20:10:00")
    val serverTime: LocalDateTime,
) {
    companion object {
        fun from(snapshot: BurstGameSnapshot): StartBurstGameResponse =
            StartBurstGameResponse(
                partyId = snapshot.partyId,
                myParticipantId = snapshot.myParticipantId,
                startedAt = snapshot.startedAt,
                endsAt = snapshot.endsAt,
                totalTapCount = snapshot.totalTapCount,
                stateVersion = snapshot.stateVersion,
                serverTime = snapshot.serverTime,
            )
    }
}
