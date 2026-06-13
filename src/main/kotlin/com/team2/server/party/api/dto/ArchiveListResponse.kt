package com.team2.server.party.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "보관함 리스트 응답")
data class ArchiveListResponse(
    @Schema(description = "보관함 항목")
    val items: List<ArchiveListItemResponse>,
    @Schema(description = "다음 페이지 cursor. 없으면 null", nullable = true, example = "1024")
    val nextCursor: String?,
    @Schema(description = "보관함 전체 개수 (헤더 표시용)", example = "37")
    val totalCount: Long,
) {
    companion object {
        val EMPTY: ArchiveListResponse =
            ArchiveListResponse(items = emptyList(), nextCursor = null, totalCount = 0)
    }
}
