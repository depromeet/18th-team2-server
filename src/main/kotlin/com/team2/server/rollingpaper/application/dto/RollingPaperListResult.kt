package com.team2.server.rollingpaper.application.dto

import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDateTime

data class ParticipantRollingPaperListResult(
    val partyOption: PartyOption,
    val liveEndAt: LocalDateTime?,
    val items: List<ParticipantRollingPaperListItemResult>,
    val pageInfo: RollingPaperPageInfoResult,
)

data class OwnerRollingPaperListResult(
    val celebrantNickname: String?,
    val partyEndAt: LocalDateTime,
    val items: List<OwnerRollingPaperListItemResult>,
    val pageInfo: RollingPaperPageInfoResult,
)

data class RollingPaperPageInfoResult(
    val page: Int,
    val totalCount: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

data class ParticipantRollingPaperListItemResult(
    val rollingPaperId: Long,
    val writerNickname: String,
    val toppingImageUrl: String,
)

data class OwnerRollingPaperListItemResult(
    val rollingPaperId: Long,
    val position: Long,
    val writerNickname: String,
    val content: String,
    val toppingImageUrl: String,
)
