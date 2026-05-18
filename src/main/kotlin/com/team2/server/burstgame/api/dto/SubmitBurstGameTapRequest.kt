package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class SubmitBurstGameTapRequest(
    @Schema(description = "이번 batch에 포함된 터치 수. 1~30 사이 값만 허용합니다.", example = "7")
    @field:Min(1)
    @field:Max(BurstGamePolicy.MAX_BATCH_TAP_COUNT)
    val tapCount: Int,
    @Schema(description = "참가자별로 증가시키는 batch 멱등성 키입니다. 이미 처리한 값은 중복 요청으로 무시됩니다.", example = "12")
    @field:Min(1)
    val clientSequence: Long,
)
