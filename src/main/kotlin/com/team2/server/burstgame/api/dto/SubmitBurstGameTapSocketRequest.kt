package com.team2.server.burstgame.api.dto

import com.team2.server.burstgame.domain.policy.BurstGamePolicy
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class SubmitBurstGameTapSocketRequest(
    // REST 경로의 SubmitBurstGameTapRequest 와 동일한 제약을 유지한다.
    @field:Min(1)
    @field:Max(BurstGamePolicy.MAX_BATCH_TAP_COUNT)
    val tapCount: Int,
    @field:Min(1)
    val clientSequence: Long,
    @field:NotBlank
    val participantToken: String,
    @field:NotBlank
    val clientRequestId: String,
)
