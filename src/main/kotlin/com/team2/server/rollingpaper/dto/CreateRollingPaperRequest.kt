package com.team2.server.rollingpaper.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "롤링페이퍼 작성 요청")
data class CreateRollingPaperRequest(
    @field:NotBlank(message = "작성자 닉네임은 필수입니다")
    @field:Size(max = 10, message = "작성자 닉네임은 10자 이하여야 합니다")
    @Schema(description = "작성자 닉네임", example = "축하요정")
    val writerNickname: String?,
    @field:NotBlank(message = "내용은 필수입니다")
    @field:Size(max = 100, message = "내용은 100자 이하여야 합니다")
    @Schema(description = "롤링페이퍼 내용", example = "생일 축하해!")
    val content: String?,
    @field:NotNull(message = "토핑 선택은 필수입니다")
    @field:Positive(message = "토핑 ID는 양수여야 합니다")
    @Schema(description = "토핑 ID", example = "1")
    val toppingId: Long?,
) {
    fun trimmedWriterNickname(): String = requireNotNull(writerNickname).trim()

    fun trimmedContent(): String = requireNotNull(content).trim()

    fun requiredToppingId(): Long = requireNotNull(toppingId)
}
